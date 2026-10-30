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
 * 익명 ID 쿠키 NLNB_AID 읽기 · 쓰기 · 지우기 (API 명세 1-2)
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

	/**
	 * 익명 ID를 쿠키로 내려준다. 새로 발급했을 때만 부른다 — 행동 API(체험 시작 API 2 · 성향 P-2 · 꾸미기 · 레벨 등)가
	 * 쿠키가 없거나 DB에 없는 값일 때, 로그인(카카오 · 아이디)이 회원에 연결된 새 익명 ID를 만들었을 때(BE-62 결정 6)
	 */
	public void write(HttpServletResponse response, UUID anonymousId) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(anonymousId.toString(), MAX_AGE).toString());
	}

	/**
	 * 익명 ID 쿠키를 지운다(Max-Age=0). 로그아웃 때 회원에 연결된 익명 ID를 브라우저에서 떼어 낸다 (BE-137).
	 * 발급과 같은 이름 · Path로 보내야 브라우저가 같은 쿠키로 보고 지우므로 같은 빌더를 쓴다
	 */
	public void clear(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
	}

	private static ResponseCookie cookie(String value, Duration maxAge) {
		return ResponseCookie.from(NAME, value)
			.httpOnly(true)
			.secure(true)
			.sameSite("Lax")
			.path("/")
			.maxAge(maxAge)
			.build();
	}

	private static Optional<UUID> parse(String value) {
		try {
			return Optional.of(UUID.fromString(value));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
