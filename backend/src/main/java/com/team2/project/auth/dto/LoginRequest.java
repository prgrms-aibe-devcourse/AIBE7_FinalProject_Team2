package com.team2.project.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 아이디 로그인 요청 (확장 단계, BE-113, API 명세 M-6)
 *
 * 허용 글자는 여기서 보지 않는다 — 글자가 틀린 아이디(예: 관리자 이메일)도 "없는 아이디"와 같은 401로 답한다(결정 3).
 * 빈 값 · 길이 상한(아이디 20자 · 비밀번호 64자)만 400 VALIDATION_ERROR다. 가입 규칙상 그보다 긴 계정은 없어
 * 계정 존재 여부가 드러나지 않는다. 비밀번호 64자는 Argon2 비교 비용 상한이기도 하다
 */
public record LoginRequest(
	@NotBlank @Size(max = 20) String id,
	@NotBlank @Size(max = 64) String password
) {

	/** 로그 · 예외 메시지에 비밀번호 · 아이디가 찍히지 않게 한다 */
	@Override
	public String toString() {
		return "LoginRequest[]";
	}
}
