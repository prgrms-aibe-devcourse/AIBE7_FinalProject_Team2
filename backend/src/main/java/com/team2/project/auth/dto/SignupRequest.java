package com.team2.project.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 아이디 회원가입 요청 (확장 단계, BE-113, API 명세 M-5). 규칙은 docs/decisions/BE-113.md 결정 2
 *
 * @param id       영문 · 숫자 · 밑줄 4~20자. 서버가 소문자로 바꿔 저장한다
 * @param password 8~64자, 영문과 숫자를 하나 이상 포함
 * @param nickname 한글 · 영문 · 숫자 2~12자(한글이 있으면 8자까지 — 서비스에서 확인)
 */
public record SignupRequest(
	@NotBlank @Pattern(regexp = "[A-Za-z0-9_]{4,20}") String id,
	@NotNull @Size(min = 8, max = 64) @Pattern(regexp = "(?s)(?=.*[A-Za-z])(?=.*[0-9]).*") String password,
	@NotBlank @Size(min = 2, max = 12) @Pattern(regexp = "[가-힣A-Za-z0-9]+") String nickname
) {

	/** 로그 · 예외 메시지에 비밀번호가 찍히지 않게 한다(아이디 · 닉네임도 남기지 않는다) */
	@Override
	public String toString() {
		return "SignupRequest[]";
	}
}
