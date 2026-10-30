package com.team2.project.profile.controller;

import com.team2.project.common.web.Requester;
import com.team2.project.profile.domain.ProfileSettingsPatch;
import com.team2.project.profile.domain.ProfileSettingsValues;
import com.team2.project.profile.service.ProfileSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 프로필 선택 설정 부분 저장 API (P-8, BE-104). 조회는 마이페이지(P-5)에 함께 내려 준다 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProfileSettingsController {

	private final ProfileSettingsService profileSettingsService;

	/** 보낸 칸만 저장 — 200 + 저장 뒤 세 값 전체. */
	@PatchMapping("/profile-settings")
	public ProfileSettingsValues patch(@RequestBody JsonNode body, Requester requester) {
		ProfileSettingsPatch patch = ProfileSettingsPatch.parse(body);	// 형식 검사를 먼저 한다
		return profileSettingsService.patch(requester.requireMember(), patch);
	}
}
