package com.team2.project.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.common.security.token.IssuedTokens;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenIssuer;
import com.team2.project.common.security.token.jwt.RefreshTokenStore;
import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.common.web.RefreshTokenCookies;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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

	// ---- 로그아웃 익명 ID 테스트용 데이터 (BE-137) ----

	private final List<Long> memberIds = new ArrayList<>();
	private final List<UUID> anonymousIds = new ArrayList<>();

	private long insertMember() {
		Long id = jdbcTemplate.queryForObject(
			"INSERT INTO member (provider, provider_user_id) VALUES ('KAKAO', ?) RETURNING id", Long.class,
			"be137-" + UUID.randomUUID());
		memberIds.add(id);
		return id;
	}

	/** memberId가 null이면 손님(연결 안 된) 익명 사용자 */
	private UUID insertAnonymousUser(Long memberId) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO anonymous_user (id, member_id) VALUES (?, ?)", id, memberId);
		anonymousIds.add(id);
		return id;
	}

	@AfterEach
	void deleteLogoutData() {
		anonymousIds.forEach(id -> jdbcTemplate.update("DELETE FROM anonymous_user WHERE id = ?", id));
		memberIds.forEach(id -> {
			jdbcTemplate.update("DELETE FROM anonymous_user WHERE member_id = ?", id);
			jdbcTemplate.update("DELETE FROM member WHERE id = ?", id);
		});
		anonymousIds.clear();
		memberIds.clear();
	}

	private static Cookie anonymousCookie(UUID id) {
		return new Cookie(AnonymousIdCookie.NAME, id.toString());
	}

	/** 응답의 Set-Cookie 중 익명 ID 쿠키를 지우는 것 */
	private static List<String> anonymousCookieDeletions(MvcResult result) {
		return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
			.filter(header -> header.startsWith(AnonymousIdCookie.NAME + "="))
			.toList();
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
	void reissue_noCookie_401TokenInvalid_andClearsCookie() throws Exception {
		MvcResult result = mockMvc.perform(post(REISSUE).header(HttpHeaders.ORIGIN, SAME_ORIGIN))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
			.andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
	}

	/** PR #218 리뷰(정민석): 재발급 실패 때 죽은 쿠키를 남겨 두면 화면이 실패하는 재발급을 계속 반복할 수 있다 */
	@Test
	void reissue_unknownCookieValue_401TokenInvalid_andClearsCookie() throws Exception {
		MvcResult result = mockMvc.perform(post(REISSUE).cookie(new Cookie(RefreshTokenCookies.NAME, "no-such-token"))
				.header(HttpHeaders.ORIGIN, SAME_ORIGIN))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
			.andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
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
	@DisplayName("로그아웃: 회원에 연결된 익명 ID면 그 쿠키도 지운다 — 공용 PC 다음 사람 기록이 회원 계정에 쌓이지 않게 (BE-137)")
	void logout_linkedAnonymousId_clearsAnonymousCookie_keepsRecordsLinked() throws Exception {
		long memberId = insertMember();
		UUID anonymousId = insertAnonymousUser(memberId);

		MvcResult result = mockMvc.perform(post(LOGOUT)
				.cookie(refreshCookie(memberId), anonymousCookie(anonymousId))
				.header(HttpHeaders.ORIGIN, SAME_ORIGIN))
			.andExpect(status().isNoContent())
			.andReturn();

		// 발급 때와 같은 이름 · Path로 Max-Age=0을 보내야 브라우저가 지운다
		assertThat(anonymousCookieDeletions(result)).singleElement().satisfies(header -> assertThat(header)
			.startsWith(AnonymousIdCookie.NAME + "=;").contains("Max-Age=0", "Path=/", "HttpOnly", "Secure"));
		// 이전 기록은 회원 계정에 그대로 남는다 (연결을 끊지 않는다)
		assertThat(jdbcTemplate.queryForObject("SELECT member_id FROM anonymous_user WHERE id = ?", Long.class,
			anonymousId)).isEqualTo(memberId);
	}

	@Test
	@DisplayName("로그아웃: 리프레시 쿠키가 이미 없어도(만료 뒤 로그아웃) 연결된 익명 ID는 지운다 (BE-137)")
	void logout_linkedAnonymousId_withoutRefreshCookie_stillClearsAnonymousCookie() throws Exception {
		UUID anonymousId = insertAnonymousUser(insertMember());

		MvcResult result = mockMvc.perform(post(LOGOUT).cookie(anonymousCookie(anonymousId))
				.header(HttpHeaders.ORIGIN, SAME_ORIGIN))
			.andExpect(status().isNoContent())
			.andReturn();

		assertThat(anonymousCookieDeletions(result)).hasSize(1);
	}

	@Test
	@DisplayName("로그아웃: 손님(연결 안 된) 익명 ID · DB에 없는 값 · 형식이 틀린 값은 지우지 않는다 (BE-137)")
	void logout_unlinkedOrUnknownAnonymousId_keepsAnonymousCookie() throws Exception {
		UUID guest = insertAnonymousUser(null);

		for (Cookie cookie : List.of(anonymousCookie(guest), anonymousCookie(UUID.randomUUID()),
			new Cookie(AnonymousIdCookie.NAME, "not-a-uuid"))) {
			MvcResult result = mockMvc.perform(post(LOGOUT).cookie(cookie).header(HttpHeaders.ORIGIN, SAME_ORIGIN))
				.andExpect(status().isNoContent())
				.andReturn();
			assertThat(anonymousCookieDeletions(result)).as("쿠키 값 %s", cookie.getValue()).isEmpty();
		}
		assertThat(jdbcTemplate.queryForObject("SELECT member_id FROM anonymous_user WHERE id = ?", Long.class,
			guest)).isNull();
	}

	@Test
	@DisplayName("로그아웃으로 익명 쿠키를 지운 브라우저가 체험을 시작하면, 회원과 연결 안 된 새 익명 ID를 받는다 (BE-137)")
	void afterLogout_startingExperience_issuesNewUnlinkedAnonymousId() throws Exception {
		long memberId = insertMember();
		UUID before = insertAnonymousUser(memberId);
		mockMvc.perform(post(LOGOUT).cookie(anonymousCookie(before)).header(HttpHeaders.ORIGIN, SAME_ORIGIN))
			.andExpect(status().isNoContent());

		// 브라우저는 지운 쿠키를 보내지 않는다 → 체험 시작이 새 익명 ID를 발급한다(BE-97 "행동했을 때만 발급")
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie issued = startAndGetCookie(caseId);

		UUID after = UUID.fromString(issued.getValue());
		assertThat(after).isNotEqualTo(before);
		assertThat(jdbcTemplate.queryForObject("SELECT member_id FROM anonymous_user WHERE id = ?", Long.class,
			after)).isNull();
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
