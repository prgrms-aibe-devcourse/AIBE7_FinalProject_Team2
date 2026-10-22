package com.team2.project.profile.service;

import com.team2.project.experience.repository.AnonymousUserRepository;
import com.team2.project.experience.service.AnonymousUserService;
import com.team2.project.experience.service.AnonymousUserService.IssuedAnonymousUser;
import com.team2.project.profile.domain.ProfileSetting;
import com.team2.project.profile.domain.ProfileSettingsPatch;
import com.team2.project.profile.domain.ProfileSettingsValues;
import com.team2.project.profile.repository.ProfileSettingRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 선택 설정 — 공개 성향카드 · 프로필카드 아래 성향 · 한 줄 소개 (API 명세 P-8 · P-5 v0.51, BE-104).
 * 프로필 꾸미기(P-7, 전체 저장)와 따로 둔다. 세 값은 화면마다 따로 바뀌므로 보낸 칸만 바꾼다(PATCH)
 */
@Service
@RequiredArgsConstructor
public class ProfileSettingsService {

	private final ProfileSettingRepository profileSettingRepository;
	private final AnonymousUserService anonymousUserService;
	private final AnonymousUserRepository anonymousUserRepository;
	private final Clock clock;

	/** 저장 결과. issuedCookieId가 있으면 컨트롤러가 Set-Cookie로 내려준다 */
	public record SaveResult(ProfileSettingsValues profileSettings, UUID issuedCookieId) {
	}

	/** 저장한 설정(없으면 기본값) — 마이페이지(P-5) profileSettings */
	@Transactional(readOnly = true)
	public ProfileSettingsValues view(UUID anonymousUserId) {
		return profileSettingRepository.findById(anonymousUserId)
			.map(ProfileSetting::values)
			.orElse(ProfileSettingsValues.DEFAULTS);
	}

	/**
	 * 부분 저장 (PATCH). 형식은 컨트롤러에 오기 전에 ProfileSettingsPatch가 검사했다(400이면 익명 ID를 발급하지 않는다).
	 * 쿠키가 없으면 P-7처럼 익명 ID를 새로 발급한다. 응답은 저장 뒤의 세 값 전체다
	 */
	@Transactional
	public SaveResult patch(Optional<UUID> anonymousId, ProfileSettingsPatch patch) {
		IssuedAnonymousUser issued = anonymousUserService.getOrIssue(anonymousId);
		UUID userId = issued.user().getId();
		// 같은 사용자의 동시 저장을 차례로 처리한다(처음 저장이 둘 다 INSERT하면 기본 키 충돌)
		anonymousUserRepository.findByIdForUpdate(userId);

		ProfileSetting setting = profileSettingRepository.findById(userId)
			.map(saved -> {
				saved.apply(patch, clock.instant());
				return saved;
			})
			.orElseGet(() -> profileSettingRepository.save(ProfileSetting.of(userId, patch, clock.instant())));
		return new SaveResult(setting.values(), issued.issued() ? userId : null);
	}
}
