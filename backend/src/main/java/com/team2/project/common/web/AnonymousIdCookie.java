package com.team2.project.common.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 익명 ID 쿠키 NLNB_AID 읽기 · 쓰기 (API 명세 1-2)
 * 속성: HttpOnly, Secure, SameSite=Lax, Path=/, 유효 기간 1년
 */
@Component
public class AnonymousIdCookie {

	public static final String NAME = "NLNB_AID";

	private static final Duration MAX_AGE = Duration.ofDays(365);

	/** 요청의 익명 ID. 쿠키가 없거나 UUID 형식이 아니면(조작 · 손상) 없는 것으로 본다 */
	public Optional<UUID> read(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
			.filter(cookie -> NAME.equals(cookie.getName()))
			.map(Cookie::getValue)
			.findFirst()
			.flatMap(AnonymousIdCookie::parse);
	}

	/** 익명 ID를 쿠키로 내려준다 (체험 시작 API 2에서만 발급) */
	public void write(HttpServletResponse response, UUID anonymousId) {
		ResponseCookie cookie = ResponseCookie.from(NAME, anonymousId.toString())
			.httpOnly(true)
			.secure(true)
			.sameSite("Lax")
			.path("/")
			.maxAge(MAX_AGE)
			.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	private static Optional<UUID> parse(String value) {
		try {
			return Optional.of(UUID.fromString(value));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
