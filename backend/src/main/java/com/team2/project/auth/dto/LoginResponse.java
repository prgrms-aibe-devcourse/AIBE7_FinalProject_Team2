package com.team2.project.auth.dto;

/**
 * 아이디 회원가입(M-5) · 로그인(M-6) 응답 (확장 단계, BE-113)
 * 토큰은 본문에 싣지 않는다 — 리프레시 토큰은 쿠키(NLNB_RT)로, 액세스 토큰은 화면이 M-1 재발급으로 받는다
 *
 * @param nickname 화면 헤더 등에 보일 표시 이름. 닉네임이 있으면 그 값, 없으면(V39가 중복 닉네임을 비운 회원) `방청객 {회원 ID}` — 항상 문자열 (BE-139)
 */
public record LoginResponse(String nickname) {
}
