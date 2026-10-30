package com.team2.project.level.service;

import com.team2.project.common.owner.Owner;
import com.team2.project.member.repository.MemberRepository;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.level.domain.Job;
import com.team2.project.level.domain.LevelXp;
import com.team2.project.level.domain.ProfileJob;
import com.team2.project.level.domain.XpSource;
import com.team2.project.level.dto.LevelStatus;
import com.team2.project.level.repository.ProfileJobRepository;
import com.team2.project.level.repository.XpEventRepository;
import com.team2.project.profile.domain.ProfileStats;
import com.team2.project.profile.service.ProfileStatsReader;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 레벨 · 직업 (BE-106, docs/decisions/BE-106.md). 레벨은 저장하지 않고 XP 합계로 계산한다.
 * XP = 기존 기록에서 계산하는 값(LevelXp: 사건 완료 · 성향 · 출석 · 업적) + 이벤트 원장(xp_event: 글 · 댓글 · 좋아요 · SNS 공유)
 */
@Service
@RequiredArgsConstructor
public class LevelService {

	/** 일일 상한을 세는 날짜 기준 (출석과 같은 한국 시간) */
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final ProfileStatsReader statsReader;
	private final XpEventRepository xpEventRepository;
	private final ProfileJobRepository profileJobRepository;
	private final ExperienceRepository experienceRepository;
	private final MemberRepository memberRepository;
	private final Clock clock;

	/** 지금 레벨 · 직업 (마이페이지 P-5 level과 같은 값) */
	@Transactional(readOnly = true)
	public LevelStatus view(long memberId) {
		return status(memberId);
	}

	/** 이미 읽은 통계를 받는 쪽: 마이페이지가 꾸미기 · 도전과제와 함께 한 번만 읽어 넘긴다 (BE-106 리뷰) */
	@Transactional(readOnly = true)
	public LevelStatus view(long memberId, ProfileStats stats) {
		return status(memberId, stats);
	}

	/** 카드에 보여 줄 직업을 고른다. 아직 열리지 않은 직업은 거절한다 (P-7 · P-8과 같다) */
	@Transactional
	public LevelStatus selectJob(long memberId, String jobCode) {
		Job job = Job.fromCode(jobCode)
			.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "알 수 없는 직업입니다."));
		memberRepository.findByIdForUpdate(memberId);	// 같은 회원의 동시 저장을 차례로 (첫 저장이 둘 다 INSERT하면 기본 키 충돌)
		if (!job.unlockedAt(status(memberId).level())) {
			throw new BusinessException(ErrorCode.JOB_LOCKED, "아직 열리지 않은 직업입니다.");
		}
		profileJobRepository.findById(memberId).ifPresentOrElse(
			saved -> saved.change(job, clock.instant()),
			() -> profileJobRepository.save(ProfileJob.of(memberId, job, clock.instant())));
		return status(memberId);
	}

	/**
	 * SNS 공유 보상 (L-3). 서버는 실제 공유 여부를 알 수 없어 화면이 부른 것을 믿는다.
	 * 그래서 완료한 사건만 받을 수 있고, 사건당 1회 · 하루 1회로 총량을 묶는다
	 * @return 받은 XP (0이면 이미 받았거나 오늘 상한을 채움)
	 */
	@Transactional
	public int rewardShare(long memberId, long caseId) {
		if (!experienceRepository.existsCompleted(new Owner.MemberOwner(memberId), caseId)) {
			throw new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND);
		}
		return award(memberId, XpSource.SNS_SHARE, String.valueOf(caseId), "");
	}

	/**
	 * 이벤트 경험치를 준다 (글 · 댓글 · 좋아요 · 공유 — 커뮤니티 BE가 부른다).
	 * 같은 사용자의 요청을 행 잠금으로 차례로 처리해, 동시에 와도 상한을 넘기지 않는다.
	 *
	 * <p>{@code memberId}는 DB에 있는 회원 ID만 넘긴다. 없는 ID면 잠금 조회가 빈 결과로 지나가고 INSERT에서 FK 오류가 난다
	 * (호출하는 쪽이 이미 확인한 회원이어야 한다). refKey · groupKey 형식은 {@link XpSource}를 따른다.
	 *
	 * @param refKey   지급 대상. 같은 (사용자, 수급처, 대상)은 한 번만 준다
	 * @param groupKey 묶음 상한을 세는 키(좋아요 받기는 글 ID). 상한이 없으면 빈 문자열
	 * @return 받은 XP (0이면 상한 · 중복으로 주지 않음)
	 */
	@Transactional
	public int award(long memberId, XpSource source, String refKey, String groupKey) {
		memberRepository.findByIdForUpdate(memberId);
		LocalDate today = clock.instant().atZone(KST).toLocalDate();
		if (source.dailyCap() > 0 && xpEventRepository.countOnDay(memberId, source.name(), today) >= source.dailyCap()) {
			return 0;
		}
		if (source.groupCap() > 0 && xpEventRepository.countInGroup(memberId, source.name(), groupKey) >= source.groupCap()) {
			return 0;
		}
		int inserted = xpEventRepository.insertIfAbsent(memberId, source.name(), refKey, groupKey, source.amount(), today);
		return inserted == 1 ? source.amount() : 0;
	}

	private LevelStatus status(long userId) {
		return status(userId, statsReader.read(userId));
	}

	private LevelStatus status(long userId, ProfileStats stats) {
		int xp = LevelXp.derived(stats) + xpEventRepository.sumAmount(userId);
		Job selected = profileJobRepository.findById(userId).map(ProfileJob::getJob).orElse(null);
		return LevelStatus.of(xp, selected);
	}
}
