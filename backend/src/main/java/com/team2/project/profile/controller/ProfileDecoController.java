package com.team2.project.profile.controller;

import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.common.web.Requester;
import com.team2.project.profile.domain.ProfileDecoValues;
import com.team2.project.profile.service.ProfileDecoService;
import com.team2.project.profile.service.ProfileDecoService.SaveResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 프로필 꾸미기 저장 API (BE-96). 조회는 마이페이지(P-5)에 함께 내려 준다 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProfileDecoController {

	private final ProfileDecoService profileDecoService;
	private final AnonymousIdCookie anonymousIdCookie;

	/** 꾸미기 전체 저장 — 200 + 저장된 값. 쿠키가 없었으면 Set-Cookie로 익명 ID 발급 */
	@PutMapping("/profile-deco")
	public ProfileDecoValues save(@RequestBody ProfileDecoValues body, Requester requester,
		HttpServletResponse response) {
		SaveResult result = profileDecoService.save(requester.anonymousId(), body);
		if (result.issuedCookieId() != null) {
			anonymousIdCookie.write(response, result.issuedCookieId());
		}
		return result.profileDeco();
	}
}
