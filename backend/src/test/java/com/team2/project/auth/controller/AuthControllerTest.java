package com.team2.project.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.common.security.token.IssuedTokens;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenIssuer;
import com.team2.project.common.security.token.jwt.RefreshTokenStore;
import com.team2.project.common.web.RefreshTokenCookies;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 재발급 · 로그아웃 API (확장 단계, BE-94 · WBS 7.4.3). 리프레시 토큰 쿠키로만 인증하고,
 * Origin 검증(OriginVerificationFilter)이 CSRF를 막는지까지 실제 보안 체인을 거쳐 확인한다.
 */
class AuthControllerTest extends ApiIntegrationTest {

	private static final String REISSUE = "/api/v1/auth/token/reissue";
	private static final String LOGOUT = "/api/v1/auth/logout";
	private static final String SAME_ORIGIN = "http://localhost";
	private static final String OTHER_ORIGIN = "https://evil.example.com";

	@Autowired
	private TokenIssuer tokenIssuer;

	@Autowired
	private RefreshTokenStore refreshTokens;

	private Cookie refreshCookie(long memberId) {
		IssuedTokens tokens = tokenIssuer.issue(MemberPrincipal.user(memberId));
		return new Cookie(RefreshTokenCookies.NAME, tokens.refreshToken());
	}

	// ---- 재발급 ----

	@Test
	void reissue_validCookie_returnsNewAccessToken_andRotatesCookie() throws Exception {
		Cookie cookie = refreshCookie(21);

		MvcResult result = mockMvc.perform(post(REISSUE).cookie(cookie).header(HttpHeaders.ORIGIN, SAME_ORIGIN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.accessTokenExpiresAt").isNotEmpty())
			.andReturn();

		String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith(RefreshTokenCookies.NAME + "=").doesNotContain("=" + cookie.getValue() + ";");
		assertThat(refreshTokens.find(cookie.getValue())).isEmpty();	// 기존 리프레시 토큰은 로테이션으로 사라졌다
	}

	@Test
	void reissue_noCookie_401TokenInvalid() throws Exception {
		mockMvc.perform(post(REISSUE).header(HttpHeaders.ORIGIN, SAME_ORIGIN))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	@Test
	void reissue_unknownCookieValue_401TokenInvalid() throws Exception {
		mockMvc.perform(post(REISSUE).cookie(new Cookie(RefreshTokenCookies.NAME, "no-such-token"))
				.header(HttpHeaders.ORIGIN, SAME_ORIGIN))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	@Test
	void reissue_wrongOrigin_403Forbidden_evenWithValidCookie() throws Exception {
		Cookie cookie = refreshCookie(22);

		mockMvc.perform(post(REISSUE).cookie(cookie).header(HttpHeaders.ORIGIN, OTHER_ORIGIN))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		assertThat(refreshTokens.find(cookie.getValue())).isPresent();	// 거절됐으니 로테이션도 안 됐다
		refreshTokens.delete(cookie.getValue());
	}

	@Test
	void reissue_noOriginOrReferer_403Forbidden() throws Exception {
		Cookie cookie = refreshCookie(23);

		mockMvc.perform(post(REISSUE).cookie(cookie))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		refreshTokens.delete(cookie.getValue());
	}

	@Test
	void reissue_matchingReferer_accepted_whenOriginHeaderMissing() throws Exception {
		Cookie cookie = refreshCookie(24);

		mockMvc.perform(post(REISSUE).cookie(cookie).header(HttpHeaders.REFERER, SAME_ORIGIN + "/mypage"))
			.andExpect(status().isOk());
	}

	/** PR #218 리뷰: getRequestURI() 원시 비교였다면 percent-encoding으로 Origin 검증을 건너뛸 수 있었다 */
	@Test
	void reissue_percentEncodedPath_stillGoesThroughOriginCheck() throws Exception {
		Cookie cookie = refreshCookie(25);
		// post(String)은 '%'를 다시 인코딩해 버리므로(%72 → %2572), 이미 인코딩된 URI를 그대로 쓴다
		URI encoded = new URI("/api/v1/auth/token/%72eissue");

		mockMvc.perform(post(encoded).cookie(cookie).header(HttpHeaders.ORIGIN, OTHER_ORIGIN))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		refreshTokens.delete(cookie.getValue());
	}

	// ---- 로그아웃 ----

	@Test
	void logout_validCookie_revokesToken_andClearsCookie() throws Exception {
		Cookie cookie = refreshCookie(31);

		MvcResult result = mockMvc.perform(post(LOGOUT).cookie(cookie).header(HttpHeaders.ORIGIN, SAME_ORIGIN))
			.andExpect(status().isNoContent())
			.andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
		assertThat(refreshTokens.find(cookie.getValue())).isEmpty();
	}

	@Test
	void logout_noCookie_stillNoContent_idempotent() throws Exception {
		mockMvc.perform(post(LOGOUT).header(HttpHeaders.ORIGIN, SAME_ORIGIN))
			.andExpect(status().isNoContent());
	}

	@Test
	void logout_wrongOrigin_403Forbidden_tokenNotRevoked() throws Exception {
		Cookie cookie = refreshCookie(32);

		mockMvc.perform(post(LOGOUT).cookie(cookie).header(HttpHeaders.ORIGIN, OTHER_ORIGIN))
			.andExpect(status().isForbidden());

		assertThat(refreshTokens.find(cookie.getValue())).isPresent();
		refreshTokens.delete(cookie.getValue());
	}
}
