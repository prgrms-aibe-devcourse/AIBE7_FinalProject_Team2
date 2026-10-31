package com.team2.project.member.controller;

import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.common.web.Requester;
import com.team2.project.member.dto.AnonymousImportCandidateResponse;
import com.team2.project.member.dto.AnonymousImportResponse;
import com.team2.project.member.dto.MemberMeResponse;
import com.team2.project.member.dto.NicknameRequest;
import com.team2.project.member.dto.NicknameResponse;
import com.team2.project.member.service.AnonymousImportService;
import com.team2.project.member.service.MemberInfoService;
import com.team2.project.member.service.MemberNicknameService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 회원 정보 · 닉네임 · 첫 로그인 가져오기 (확장 단계, BE-139 · BE-140, API 명세 M-7 ~ M-11). 로그인한 회원만(Authorization: Bearer) —
 * SecurityConfig가 먼저 막고(401 UNAUTHORIZED), 여기서도 회원이 없으면 같은 401로 끝낸다
 */
@RestController
@RequestMapping("/api/v1/members/me")
@RequiredArgsConstructor
public class MemberController {

	private final MemberNicknameService nicknameService;
	private final MemberInfoService infoService;
	private final AnonymousImportService anonymousImportService;
	private final AnonymousIdCookie anonymousIdCookie;

	/** M-8 내 회원 정보 */
	@GetMapping
	public MemberMeResponse me(Requester requester) {
		return infoService.me(requester.requireMember());
	}

	/** M-7 닉네임 설정 · 수정 — 200 + 저장한 닉네임 */
	@PutMapping("/nickname")
	public NicknameResponse changeNickname(@RequestBody NicknameRequest body, Requester requester) {
		return new NicknameResponse(nicknameService.change(requester.requireMember(), body.nickname()));
	}

	/** M-9 가져올 수 있는 이 브라우저의 기록. 없으면 404 */
	@GetMapping("/anonymous-import")
	public AnonymousImportCandidateResponse anonymousImport(Requester requester) {
		return anonymousImportService.candidate(requester.requireMember(), requester.anonymousId());
	}

	/** M-10 이 브라우저의 체험을 회원 것으로 옮긴다 — 200 + 옮긴 체험 수 */
	@PostMapping("/anonymous-import")
	public AnonymousImportResponse importAnonymous(Requester requester) {
		return anonymousImportService.importExperiences(requester.requireMember(), requester.anonymousId());
	}

	/** M-11 옮기지 않기로 정한다 — 204, 이 브라우저의 익명 ID 쿠키를 지운다 */
	@PostMapping("/anonymous-import/discard")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void discardAnonymous(Requester requester, HttpServletResponse response) {
		anonymousImportService.discard(requester.requireMember(), requester.anonymousId());
		anonymousIdCookie.clear(response);
	}
}
