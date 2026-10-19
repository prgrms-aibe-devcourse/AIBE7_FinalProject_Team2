package com.team2.project.common.security.token;

/**
 * 토큰 발급기 (확장 단계, BE-57 · WBS 7.4.1에서 계약만 정의)
 *
 * 구현: C(WBS 7.4.3, JWT 발급 + 리프레시 토큰 Redis 저장).
 * 호출: D(WBS 7.4.2, 카카오 로그인 성공 처리 — 회원을 찾거나 만든 뒤 발급받아 화면에 넘긴다).
 *
 * 재발급(리프레시 토큰 교체) · 폐기(로그아웃 · 블랙리스트) · 재사용 탐지는 C가 7.4.3에서 별도 계약으로 정한다.
 * 리프레시 토큰을 쿠키로 내려보내면 재발급 · 로그아웃 요청에 CSRF 대책(CSRF 토큰 또는 출처 검증)과
 * HttpOnly · Secure · SameSite가 필요하다(docs/tech-stack.md 3장).
 */
public interface TokenIssuer {

	/** 회원에게 액세스 · 리프레시 토큰을 새로 발급한다 */
	IssuedTokens issue(MemberPrincipal member);
}
