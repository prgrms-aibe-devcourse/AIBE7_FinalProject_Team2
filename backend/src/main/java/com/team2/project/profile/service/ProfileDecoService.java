package com.team2.project.profile.service;

import com.team2.project.member.repository.MemberRepository;
import com.team2.project.profile.domain.AchievementJudge;
import com.team2.project.profile.domain.AchievementProgress;
import com.team2.project.profile.domain.ProfileDeco;
import com.team2.project.profile.domain.ProfileDecoCatalog;
import com.team2.project.profile.domain.ProfileDecoValidator;
import com.team2.project.profile.domain.ProfileDecoValues;
import com.team2.project.profile.domain.ProfileStats;
import com.team2.project.profile.domain.Rewards;
import com.team2.project.profile.repository.ProfileDecoRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 꾸미기 저장 · 조회와 도전과제 판정 (BE-96, docs/decisions/BE-96.md)
 */
@Service
@RequiredArgsConstructor
public class ProfileDecoService {

	private final ProfileDecoRepository profileDecoRepository;
	private final ProfileStatsReader statsReader;
	private final MemberRepository memberRepository;
	private final Clock clock;

	/** 마이페이지에 내려 줄 꾸미기 · 도전과제 · 열린 항목 */
	public record ProfileDecoView(ProfileDecoValues profileDeco, List<AchievementProgress> achievements, Rewards unlocked) {
	}

	/** 기록이 없는 사용자: 기본값 · 진행도 0 · 처음부터 열린 것 */
	public static ProfileDecoView emptyView() {
		List<AchievementProgress> progress = AchievementJudge.progress(ProfileStats.EMPTY);
		return new ProfileDecoView(ProfileDecoCatalog.DEFAULTS, progress, AchievementJudge.unlocked(progress));
	}

	/**
	 * 저장된 꾸미기(없으면 기본값)와 도전과제 판정. 조회는 거절하지 않는다 —
	 * 표가 바뀌어 이미 고른 항목이 잠겨도 저장값을 그대로 내려 주고, 화면이 기본값으로 바꿔 보여 준다
	 */
	@Transactional(readOnly = true)
	public ProfileDecoView view(long memberId) {
		return view(memberId, statsReader.read(memberId));
	}

	/** 이미 읽은 통계를 받는 쪽: 마이페이지가 레벨과 함께 한 번만 읽어 넘긴다 (BE-106 리뷰) */
	@Transactional(readOnly = true)
	public ProfileDecoView view(long memberId, ProfileStats stats) {
		List<AchievementProgress> progress = AchievementJudge.progress(stats);
		ProfileDecoValues saved = profileDecoRepository.findById(memberId)
			.map(ProfileDeco::values)
			.orElse(ProfileDecoCatalog.DEFAULTS);
		return new ProfileDecoView(saved, progress, AchievementJudge.unlocked(progress));
	}

	/**
	 * 꾸미기 전체 저장 (PUT). 형식 · 값 목록(400) → 잠김(409) 순으로 검사하고, 하나라도 걸리면 저장하지 않는다. 저장값과 같은 칸은 잠김을 다시 보지 않는다(BE-98)
	 */
	@Transactional
	public ProfileDecoValues save(long memberId, ProfileDecoValues values) {
		// 같은 회원의 동시 저장을 차례로 처리한다(처음 저장이 둘 다 INSERT하면 기본 키 충돌)
		memberRepository.findByIdForUpdate(memberId);
		Optional<ProfileDeco> existing = profileDecoRepository.findById(memberId);
		// 저장값과 같은 칸은 잠김을 다시 검사하지 않는다(BE-98): 표가 바뀌어 저장값이 잠겨도 다른 칸은 저장할 수 있게
		ProfileDecoValidator.validate(values,
			AchievementJudge.unlocked(AchievementJudge.progress(statsReader.read(memberId))),
			existing.map(ProfileDeco::values).orElse(null));
		ProfileDeco deco = existing
			.map(saved -> {
				saved.apply(values, clock.instant());
				return saved;
			})
			.orElseGet(() -> profileDecoRepository.save(ProfileDeco.of(memberId, values, clock.instant())));
		return deco.values();
	}
}
