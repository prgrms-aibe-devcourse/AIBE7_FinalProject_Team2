package com.team2.project.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * 아이디 회원가입 요청 (확장 단계, BE-113, API 명세 M-5). 규칙은 docs/decisions/BE-113.md 결정 2
 *
 * @param id       영문 · 숫자 · 밑줄 4~20자. 서버가 소문자로 바꿔 저장한다
 * @param password 8~64자, 영문과 숫자를 하나 이상 포함
 * @param nickname 한글 · 영문 · 숫자 2~12자(한글이 있으면 8자까지 — 서비스에서 확인)
 * @param agreed   이용약관 · 개인정보처리방침 동의(BE-133). JSON {@code true}만 받는다({@link #isAgreed()}).
 *                 Boolean이 아니라 JsonNode로 받는 이유: Jackson은 문자열 "true" · 숫자 1도 Boolean.TRUE로 바꿔 받는다(스칼라 변환 기본 켜짐).
 *                 전역 설정을 바꾸면 다른 API의 "36" 같은 문자열 숫자 허용(BE-22)까지 바뀌므로 이 칸만 직접 본다 — PersonalitySubmitRequest.baseline과 같은 방식 (PR #349 리뷰)
 */
public record SignupRequest(
	@NotBlank @Pattern(regexp = "[A-Za-z0-9_]{4,20}") String id,
	@NotNull @Size(min = 8, max = 64) @Pattern(regexp = "(?s)(?=.*[A-Za-z])(?=.*[0-9]).*") String password,
	@NotBlank @Size(min = 2, max = 12) @Pattern(regexp = "[가-힣A-Za-z0-9]+") String nickname,
	JsonNode agreed
) {

	/**
	 * 약관에 동의했는가 — JSON 불리언 true일 때만. 없음 · null · false · 문자열 "true" · 숫자 1은 모두 400(details: agreed · AssertTrue).
	 * 검사 결과의 칸 이름은 이 메서드의 속성 이름 agreed라 화면이 동의 줄에 안내를 붙인다
	 */
	@AssertTrue
	public boolean isAgreed() {
		return agreed != null && agreed.isBoolean() && agreed.booleanValue();
	}

	/** 로그 · 예외 메시지에 비밀번호가 찍히지 않게 한다(아이디 · 닉네임도 남기지 않는다) */
	@Override
	public String toString() {
		return "SignupRequest[]";
	}
}
