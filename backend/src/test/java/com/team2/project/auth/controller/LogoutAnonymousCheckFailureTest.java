package com.team2.project.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.common.security.token.IssuedTokens;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenIssuer;
import com.team2.project.common.security.token.jwt.RefreshTokenStore;
import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.common.web.RefreshTokenCookies;
import com.team2.project.member.service.AnonymousLinkService;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * 로그아웃 중 익명 ID 연결 확인이 실패해도(어떤 실행 중 예외든) 로그아웃은 204로 끝나고, 익명 ID 쿠키는 안전한 쪽(지움)으로 처리한다 (BE-137 리뷰 · PR #340 리뷰).
 * 남겨 두면 다음 사람 기록이 회원 계정에 쌓일 수 있고, 손님 값이었다면 다음 행동 때 새 익명 ID를 받는 것으로 끝난다
 */
class LogoutAnonymousCheckFailureTest extends ApiIntegrationTest {

	private static final String LOGOUT = "/api/v1/auth/logout";
	private static final String SAME_ORIGIN = "http://localhost";

	@MockitoSpyBean
	private AnonymousLinkService anonymousLinkService;

	@Autowired
	private TokenIssuer tokenIssuer;

	@Autowired
	private RefreshTokenStore refreshTokens;

	private MvcResult logout(Cookie refresh) throws Exception {
		return mockMvc.perform(post(LOGOUT)
				.cookie(refresh, new Cookie(AnonymousIdCookie.NAME, UUID.randomUUID().toString()))
				.header(HttpHeaders.ORIGIN, SAME_ORIGIN))
			.andExpect(status().isNoContent())
			.andReturn();
	}

	private static long anonymousCookieDeletions(MvcResult result) {
		return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
			.filter(header -> header.startsWith(AnonymousIdCookie.NAME + "=") && header.contains("Max-Age=0"))
			.count();
	}

	@Test
	@DisplayName("연결 확인 중 DB 오류여도 로그아웃은 204, 리프레시는 폐기되고 익명 ID 쿠키는 지운다")
	void dataAccessFailure_logoutSucceeds_clearsAnonymousCookie() throws Exception {
		IssuedTokens tokens = tokenIssuer.issue(MemberPrincipal.user(41));
		doThrow(new DataAccessResourceFailureException("DB 오류")).when(anonymousLinkService).isLinkedToMember(any());

		MvcResult result = logout(new Cookie(RefreshTokenCookies.NAME, tokens.refreshToken()));

		assertThat(anonymousCookieDeletions(result)).isEqualTo(1);
		assertThat(refreshTokens.find(tokens.refreshToken())).isEmpty();
	}

	@Test
	@DisplayName("DB 계열이 아닌 예외(코드 오류 등)여도 로그아웃은 204, 리프레시는 폐기되고 익명 ID 쿠키는 지운다 (PR #340 리뷰)")
	void unexpectedRuntimeException_logoutSucceeds_clearsAnonymousCookie() throws Exception {
		IssuedTokens tokens = tokenIssuer.issue(MemberPrincipal.user(43));
		doThrow(new IllegalStateException("예상하지 못한 오류")).when(anonymousLinkService).isLinkedToMember(any());

		MvcResult result = logout(new Cookie(RefreshTokenCookies.NAME, tokens.refreshToken()));

		assertThat(anonymousCookieDeletions(result)).isEqualTo(1);
		assertThat(refreshTokens.find(tokens.refreshToken())).isEmpty();
	}

	@Test
	@DisplayName("트랜잭션을 못 열어도(연결 풀 고갈) 로그아웃은 204, 익명 ID 쿠키는 지운다")
	void transactionFailure_logoutSucceeds_clearsAnonymousCookie() throws Exception {
		IssuedTokens tokens = tokenIssuer.issue(MemberPrincipal.user(42));
		doThrow(new CannotCreateTransactionException("풀 고갈")).when(anonymousLinkService).isLinkedToMember(any());

		MvcResult result = logout(new Cookie(RefreshTokenCookies.NAME, tokens.refreshToken()));

		assertThat(anonymousCookieDeletions(result)).isEqualTo(1);
	}
}
