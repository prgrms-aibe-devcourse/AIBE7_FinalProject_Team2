package com.team2.project.member.controller;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.member.dto.NicknameRequest;
import com.team2.project.member.dto.NicknameResponse;
import com.team2.project.member.service.MemberNicknameService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 회원 정보 (확장 단계, BE-139, API 명세 M-7). 로그인한 회원만(Authorization: Bearer) —
 * SecurityConfig가 먼저 막고(401 UNAUTHORIZED), 여기서도 회원이 없으면 같은 401로 끝낸다
 */
@RestController
@RequestMapping("/api/v1/members/me")
@RequiredArgsConstructor
public class MemberController {

	private final MemberNicknameService nicknameService;

	/** M-7 닉네임 설정 · 수정 — 200 + 저장한 닉네임 */
	@PutMapping("/nickname")
	public NicknameResponse changeNickname(@RequestBody NicknameRequest body,
		@AuthenticationPrincipal MemberPrincipal member) {
		if (member == null) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		return new NicknameResponse(nicknameService.change(member.memberId(), body.nickname()));
	}
}
