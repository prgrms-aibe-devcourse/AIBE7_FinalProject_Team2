package com.team2.project.member.controller;

import com.team2.project.common.web.Requester;
import com.team2.project.member.dto.TermsAgreementRequest;
import com.team2.project.member.service.MemberTermsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기존 회원의 약관 동의 (확장 단계, BE-145, API 명세 M-14). 로그인한 회원만(Authorization: Bearer) —
 * SecurityConfig의 /api/v1/members/me/**가 먼저 막는다(401 UNAUTHORIZED). 내 회원 정보(M-8)의 termsRequired가 true일 때 부른다
 */
@RestController
@RequestMapping("/api/v1/members/me/terms-agreement")
@RequiredArgsConstructor
public class MemberTermsController {

	private final MemberTermsService termsService;

	/** 동의를 기록한다 — 204. 동의하지 않았으면(없음 · false · 문자열 · 숫자) 400. 이미 동의한 회원이면 기록을 바꾸지 않고 204 */
	@PostMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void agree(@Valid @RequestBody TermsAgreementRequest body, Requester requester) {
		termsService.agree(requester.requireMember());
	}
}
