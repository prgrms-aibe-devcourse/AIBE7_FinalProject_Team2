package com.team2.project.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RefreshTokenCookiesTest {

	private final RefreshTokenCookies cookies = new RefreshTokenCookies();

	@Test
	@DisplayName("쿠키가 없거나 값이 비어 있으면 리프레시 토큰이 없는 것으로 본다")
	void read_missingOrBlank_returnsEmpty() {
		MockHttpServletRequest noCookie = new MockHttpServletRequest();
		MockHttpServletRequest blank = new MockHttpServletRequest();
		blank.setCookies(new Cookie(RefreshTokenCookies.NAME, ""));

		assertThat(cookies.read(noCookie)).isEmpty();
		assertThat(cookies.read(blank)).isEmpty();
	}

	@Test
	@DisplayName("NLNB_RT 쿠키 값을 읽는다")
	void read_validCookie_returnsToken() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie("OTHER", "x"), new Cookie(RefreshTokenCookies.NAME, "refresh-value"));

		assertThat(cookies.read(request)).contains("refresh-value");
	}

	@Test
	@DisplayName("발급 쿠키 속성은 HttpOnly · Secure · SameSite=Strict · 인증 경로로 좁힌 Path (C 결정)")
	void write_issuedCookie_hasDecidedAttributes() {
		MockHttpServletResponse response = new MockHttpServletResponse();

		cookies.write(response, "refresh-value", Duration.ofDays(14));

		assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
			.startsWith(RefreshTokenCookies.NAME + "=refresh-value")
			.contains("Path=" + RefreshTokenCookies.PATH, "Max-Age=1209600", "Secure", "HttpOnly", "SameSite=Strict");
	}

	@Test
	@DisplayName("로그아웃 시 Max-Age=0으로 쿠키를 지운다")
	void clear_setsExpiredCookie() {
		MockHttpServletResponse response = new MockHttpServletResponse();

		cookies.clear(response);

		assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
			.startsWith(RefreshTokenCookies.NAME + "=")
			.contains("Max-Age=0");
	}
}
