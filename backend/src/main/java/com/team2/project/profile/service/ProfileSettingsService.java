package com.team2.project.profile.service;

import com.team2.project.member.repository.MemberRepository;
import com.team2.project.profile.domain.ProfileSetting;
import com.team2.project.profile.domain.ProfileSettingsPatch;
import com.team2.project.profile.domain.ProfileSettingsValues;
import com.team2.project.profile.repository.ProfileSettingRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 선택 설정 — 공개 성향카드 · 프로필카드 아래 성향 · 한 줄 소개 (API 명세 P-8 · P-5 v0.52, BE-104).
 * 프로필 꾸미기(P-7, 전체 저장)와 따로 둔다. 세 값은 화면마다 따로 바뀌므로 보낸 칸만 바꾼다(PATCH)
 */
@Service
@RequiredArgsConstructor
public class ProfileSettingsService {

	private final ProfileSettingRepository profileSettingRepository;
	private final MemberRepository memberRepository;
	private final Clock clock;

	/** 저장한 설정(없으면 기본값) — 마이페이지(P-5) profileSettings */
	@Transactional(readOnly = true)
	public ProfileSettingsValues view(long memberId) {
		return profileSettingRepository.findById(memberId)
			.map(ProfileSetting::values)
			.orElse(ProfileSettingsValues.DEFAULTS);
	}

	/**
	 * 부분 저장 (PATCH). 형식은 컨트롤러에 오기 전에 ProfileSettingsPatch가 검사했다(400이면 저장하지 않는다). 응답은 저장 뒤의 세 값 전체다
	 */
	@Transactional
	public ProfileSettingsValues patch(long memberId, ProfileSettingsPatch patch) {
		// 같은 회원의 동시 저장을 차례로 처리한다(처음 저장이 둘 다 INSERT하면 기본 키 충돌)
		memberRepository.findByIdForUpdate(memberId);
		ProfileSetting setting = profileSettingRepository.findById(memberId)
			.map(saved -> {
				saved.apply(patch, clock.instant());
				return saved;
			})
			.orElseGet(() -> profileSettingRepository.save(ProfileSetting.of(memberId, patch, clock.instant())));
		return setting.values();
	}
}
