package com.team2.project.common.security.token;

/**
 * 액세스 토큰 판독기 (확장 단계, BE-57 · WBS 7.4.1에서 계약만 정의)
 *
 * 구현: C(WBS 7.4.3, JWT 서명 · 만료 · 블랙리스트 확인). 구현을 스프링 빈으로 하나 등록하면
 * 사용자 체인의 Bearer 필터(SecurityConfig)가 자동으로 쓴다. 빈이 없으면 Bearer 헤더가 붙은 요청은 401 TOKEN_INVALID다.
 *
 * 규칙
 * - 토큰 자체의 문제(형식 · 서명 · 만료 · 폐기)는 {@link TokenException}으로 알린다
 * - 인프라 오류(Redis 연결 실패 등)는 TokenException으로 감싸지 않고 그대로 던진다(500)
 * - 토큰 값 · 회원 정보를 로그에 남기지 않는다
 */
public interface AccessTokenVerifier {

	/**
	 * @param accessToken "Bearer " 뒤의 토큰 문자열(앞뒤 공백 제거됨, 비어 있지 않음)
	 * @return 토큰 주인
	 * @throws TokenException 잘못됐거나 만료된 토큰
	 */
	MemberPrincipal verify(String accessToken);
}
