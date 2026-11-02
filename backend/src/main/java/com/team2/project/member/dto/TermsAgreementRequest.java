package com.team2.project.member.dto;

import jakarta.validation.constraints.AssertTrue;
import tools.jackson.databind.JsonNode;

/**
 * 약관 동의 요청 (확장 단계, BE-145, API 명세 M-12 · M-14). 아이디 가입(M-5)의 {@code agreed}와 같은 규칙이다.
 *
 * @param agreed 이용약관 · 개인정보처리방침 동의. JSON {@code true}만 받는다({@link #isAgreed()}).
 *               Boolean이 아니라 JsonNode로 받는 이유는 SignupRequest와 같다 — Jackson은 문자열 "true" · 숫자 1도
 *               Boolean.TRUE로 바꿔 받는데, 전역 설정을 바꾸면 다른 API까지 바뀐다(BE-133, PR #349 리뷰)
 */
public record TermsAgreementRequest(JsonNode agreed) {

	/** 약관에 동의했는가 — JSON 불리언 true일 때만. 없음 · null · false · 문자열 "true" · 숫자 1은 모두 400(details: agreed · AssertTrue) */
	@AssertTrue
	public boolean isAgreed() {
		return agreed != null && agreed.isBoolean() && agreed.booleanValue();
	}
}
