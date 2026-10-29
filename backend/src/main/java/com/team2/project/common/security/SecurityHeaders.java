package com.team2.project.common.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * 모든 보안 필터 체인(사용자 · 관리자 · 카카오 로그인)이 함께 쓰는 응답 헤더 설정 (BE-57 · COMMON-67).
 * Spring Security 기본값(X-Content-Type-Options · X-Frame-Options: DENY · Cache-Control 등)은 그대로 두고 두 가지만 바꾼다.
 * - Referrer-Policy: strict-origin-when-cross-origin (기본값은 헤더 없음)
 * - HSTS: 끈다. 운영 Nginx가 화면 · /api 응답에 한 값으로 붙인다(frontend/nginx/default.conf $hsts_header).
 *   그대로 두면 Nginx가 넘기는 X-Forwarded-Proto: https 때문에 Spring 기본값(1년 · includeSubDomains)이 나가
 *   BE-75의 "짧게 시작" 결정을 넘는다 (docs/tech-stack.md 3장 "보안 헤더", docs/decisions/COMMON-67.md)
 */
public final class SecurityHeaders {

	public static final ReferrerPolicy REFERRER_POLICY = ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN;

	private SecurityHeaders() {
	}

	/** 체인마다 {@code http.headers(SecurityHeaders::apply)}로 건다 */
	public static void apply(HeadersConfigurer<HttpSecurity> headers) {
		headers
			.referrerPolicy(referrer -> referrer.policy(REFERRER_POLICY))
			.httpStrictTransportSecurity(HeadersConfigurer.HstsConfig::disable);
	}
}
