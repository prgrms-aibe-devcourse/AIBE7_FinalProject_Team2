package com.team2.project.profile.service;

import com.team2.project.experience.repository.AnonymousUserRepository;
import com.team2.project.experience.service.AnonymousUserService;
import com.team2.project.experience.service.AnonymousUserService.IssuedAnonymousUser;
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
import java.util.UUID;
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
	private final AnonymousUserService anonymousUserService;
	private final AnonymousUserRepository anonymousUserRepository;
	private final Clock clock;

	/** 마이페이지에 내려 줄 꾸미기 · 도전과제 · 열린 항목 */
	public record ProfileDecoView(ProfileDecoValues profileDeco, List<AchievementProgress> achievements, Rewards unlocked) {
	}

	/** 저장 결과. issuedCookieId가 있으면 컨트롤러가 Set-Cookie로 내려준다 */
	public record SaveResult(ProfileDecoValues profileDeco, UUID issuedCookieId) {
	}

	/** 기록이 없는(쿠키가 없는) 사용자: 기본값 · 진행도 0 · 처음부터 열린 것 */
	public static ProfileDecoView emptyView() {
		List<AchievementProgress> progress = AchievementJudge.progress(ProfileStats.EMPTY);
		return new ProfileDecoView(ProfileDecoCatalog.DEFAULTS, progress, AchievementJudge.unlocked(progress));
	}

	/**
	 * 저장된 꾸미기(없으면 기본값)와 도전과제 판정. 조회는 거절하지 않는다 —
	 * 표가 바뀌어 이미 고른 항목이 잠겨도 저장값을 그대로 내려 주고, 화면이 기본값으로 바꿔 보여 준다
	 */
	@Transactional(readOnly = true)
	public ProfileDecoView view(UUID anonymousUserId) {
		return view(anonymousUserId, statsReader.read(anonymousUserId));
	}

	/** 이미 읽은 통계를 받는 쪽: 마이페이지가 레벨과 함께 한 번만 읽어 넘긴다 (BE-106 리뷰) */
	@Transactional(readOnly = true)
	public ProfileDecoView view(UUID anonymousUserId, ProfileStats stats) {
		List<AchievementProgress> progress = AchievementJudge.progress(stats);
		ProfileDecoValues saved = profileDecoRepository.findById(anonymousUserId)
			.map(ProfileDeco::values)
			.orElse(ProfileDecoCatalog.DEFAULTS);
		return new ProfileDecoView(saved, progress, AchievementJudge.unlocked(progress));
	}

	/**
	 * 꾸미기 전체 저장 (PUT). 쿠키가 없으면 익명 ID를 새로 발급한다(사용자가 직접 고른 행동이라 P-2와 같다).
	 * 형식 · 값 목록(400) → 잠김(409) 순으로 검사하고, 하나라도 걸리면 저장하지 않는다. 저장값과 같은 칸은 잠김을 다시 보지 않는다(BE-98)
	 */
	@Transactional
	public SaveResult save(Optional<UUID> anonymousId, ProfileDecoValues values) {
		IssuedAnonymousUser issued = anonymousUserService.getOrIssue(anonymousId);
		UUID userId = issued.user().getId();
		// 같은 사용자의 동시 저장을 차례로 처리한다(처음 저장이 둘 다 INSERT하면 기본 키 충돌)
		anonymousUserRepository.findByIdForUpdate(userId);

		Optional<ProfileDeco> existing = profileDecoRepository.findById(userId);
		// 저장값과 같은 칸은 잠김을 다시 검사하지 않는다(BE-98): 표가 바뀌어 저장값이 잠겨도 다른 칸은 저장할 수 있게
		ProfileDecoValidator.validate(values,
			AchievementJudge.unlocked(AchievementJudge.progress(statsReader.read(userId))),
			existing.map(ProfileDeco::values).orElse(null));

		ProfileDeco deco = existing
			.map(saved -> {
				saved.apply(values, clock.instant());
				return saved;
			})
			.orElseGet(() -> profileDecoRepository.save(ProfileDeco.of(userId, values, clock.instant())));
		return new SaveResult(deco.values(), issued.issued() ? userId : null);
	}
}
