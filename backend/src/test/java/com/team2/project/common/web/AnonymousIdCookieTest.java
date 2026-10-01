package com.team2.project.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AnonymousIdCookieTest {

	private final AnonymousIdCookie anonymousIdCookie = new AnonymousIdCookie();

	@Test
	@DisplayName("쿠키가 없거나 UUID 형식이 아니면 익명 ID가 없는 것으로 본다")
	void read_missingOrMalformed_returnsEmpty() {
		MockHttpServletRequest noCookie = new MockHttpServletRequest();
		MockHttpServletRequest malformed = new MockHttpServletRequest();
		malformed.setCookies(new Cookie(AnonymousIdCookie.NAME, "not-a-uuid"));

		assertThat(anonymousIdCookie.read(noCookie)).isEmpty();
		assertThat(anonymousIdCookie.read(malformed)).isEmpty();
	}

	@Test
	@DisplayName("NLNB_AID 쿠키의 UUID를 읽는다")
	void read_validCookie_returnsUuid() {
		UUID id = UUID.randomUUID();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie("OTHER", "x"), new Cookie(AnonymousIdCookie.NAME, id.toString()));

		assertThat(anonymousIdCookie.read(request)).contains(id);
	}

	@Test
	@DisplayName("발급 쿠키 속성은 HttpOnly · Secure · SameSite=Lax · Path=/ · 1년 (API 명세 1-2)")
	void write_issuedCookie_hasSpecAttributes() {
		UUID id = UUID.randomUUID();
		MockHttpServletResponse response = new MockHttpServletResponse();

		anonymousIdCookie.write(response, id);

		assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
			.startsWith(AnonymousIdCookie.NAME + "=" + id)
			.contains("Path=/", "Max-Age=31536000", "Secure", "HttpOnly", "SameSite=Lax");
	}
}
