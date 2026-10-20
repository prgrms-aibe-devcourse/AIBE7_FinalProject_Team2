package com.team2.project.common.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰 쿠키 NLNB_RT 읽기 · 쓰기 (확장 단계, BE-94 · WBS 7.4.3, C 결정)
 *
 * 액세스 토큰은 쿠키에 담지 않는다(화면이 재발급 API로 받아 메모리에만 들고 있는다). 쿠키를 다루는 코드를
 * 여기 한 곳에 모아, 카카오 로그인 성공 처리(BE-62)도 같은 규칙으로 쿠키를 심게 한다.
 *
 * 속성(C 결정, docs/tech-stack.md 3장 "리프레시 토큰 쿠키"):
 * - HttpOnly: 자바스크립트가 읽지 못하게(XSS로 탈취 방지)
 * - Secure: HTTPS에서만 전송
 * - SameSite=Strict: 재발급 · 로그아웃은 항상 우리 화면이 같은 사이트에서 호출한다(카카오 콜백 리다이렉트
 *   자체는 Set-Cookie 응답이라 SameSite의 영향을 받지 않는다). Origin 검증(OriginVerificationFilter)과 이중으로 막는다
 * - Path: 인증 엔드포인트에서만 보내지도록 좁힌다(불필요한 노출을 줄인다)
 */
@Component
public class RefreshTokenCookies {

	public static final String NAME = "NLNB_RT";

	/** 재발급 · 로그아웃 API가 있는 경로. 이 쿠키가 다른 API 요청에는 실리지 않는다 */
	public static final String PATH = "/api/v1/auth";

	/** 쿠키를 지울 때 쓰는 만료(과거) Max-Age */
	private static final Duration DELETE = Duration.ZERO;

	/** 요청의 리프레시 토큰. 쿠키가 없으면 비어 있다 */
	public Optional<String> read(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
			.filter(cookie -> NAME.equals(cookie.getName()))
			.map(Cookie::getValue)
			.filter(value -> value != null && !value.isBlank())
			.findFirst();
	}

	/** 리프레시 토큰을 쿠키로 내려준다(로그인 성공 · 재발급 때) */
	public void write(HttpServletResponse response, String refreshToken, Duration maxAge) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(refreshToken, maxAge).toString());
	}

	/** 로그아웃 때 쿠키를 지운다 */
	public void clear(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie("", DELETE).toString());
	}

	private static ResponseCookie cookie(String value, Duration maxAge) {
		return ResponseCookie.from(NAME, value)
			.httpOnly(true)
			.secure(true)
			.sameSite("Strict")
			.path(PATH)
			.maxAge(maxAge)
			.build();
	}
}
