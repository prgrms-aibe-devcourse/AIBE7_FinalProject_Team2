package com.team2.project.auth.oauth;

import com.team2.project.common.security.token.IssuedTokens;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenIssuer;
import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.common.web.RefreshTokenCookies;
import com.team2.project.member.domain.AuthProvider;
import com.team2.project.member.service.MemberLoginService;
import com.team2.project.member.service.MemberLoginService.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * 카카오 로그인 성공 처리 (확장 단계, BE-62 · WBS 7.4.2)
 *
 * 1) 회원 찾기 · 만들기 + 익명 기록 연결(MemberLoginService). 새 익명 ID를 발급했으면 익명 쿠키를 바꿔 심는다
 * 2) TokenIssuer로 토큰 발급(BE-94) → 리프레시 토큰만 HttpOnly 쿠키(RefreshTokenCookies)
 * 3) 화면 경로로 리다이렉트. 토큰은 URL에 싣지 않는다 — 화면이 재발급 API로 액세스 토큰을 받는다
 * 기본 성공 처리(SavedRequestAware...)는 세션을 쓰므로 쓰지 않는다(#106 · 현중님 리뷰 합의)
 *
 * 1) ~ 2)에서 오류가 나면(DB · Redis 장애, 공급자 응답 이상) JSON 500 대신 실패 화면으로 보낸다 — 브라우저 이동 경로라
 * 사용자가 오류 JSON을 보게 되기 때문이다. 원인은 서버 로그에만 남긴다(토큰 · 개인정보는 남기지 않는다)
 */
@Slf4j
class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

	private final MemberLoginService memberLoginService;
	private final TokenIssuer tokenIssuer;
	private final RefreshTokenCookies refreshTokenCookies;
	private final AnonymousIdCookie anonymousIdCookie;
	private final Clock clock;
	private final String redirectPath;
	private final String failureRedirectPath;

	OAuthLoginSuccessHandler(MemberLoginService memberLoginService, TokenIssuer tokenIssuer,
		RefreshTokenCookies refreshTokenCookies, AnonymousIdCookie anonymousIdCookie, Clock clock, String redirectPath,
		String failureRedirectPath) {
		this.memberLoginService = memberLoginService;
		this.tokenIssuer = tokenIssuer;
		this.refreshTokenCookies = refreshTokenCookies;
		this.anonymousIdCookie = anonymousIdCookie;
		this.clock = clock;
		this.redirectPath = redirectPath;
		this.failureRedirectPath = failureRedirectPath;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
		Authentication authentication) throws IOException {
		LoginResult result;
		IssuedTokens tokens;
		try {
			OAuth2User kakaoUser = (OAuth2User) authentication.getPrincipal();
			// 이름 속성은 카카오 회원번호(user-name-attribute = id)
			result = memberLoginService.login(AuthProvider.KAKAO, kakaoUser.getName(), anonymousIdCookie.read(request));
			tokens = tokenIssuer.issue(MemberPrincipal.user(result.memberId()));
		} catch (RuntimeException e) {
			log.error("카카오 로그인 성공 처리 실패: {}", e.getClass().getSimpleName(), e);
			response.sendRedirect(failureRedirectPath);
			return;
		}
		// 쿠키는 회원 처리 · 토큰 발급이 모두 끝난 뒤에만 심는다(중간에 실패하면 아무 쿠키도 바뀌지 않게)
		result.issuedAnonymousId().ifPresent(id -> anonymousIdCookie.write(response, id));
		Duration remaining = Duration.between(clock.instant(), tokens.refreshTokenExpiresAt());
		refreshTokenCookies.write(response, tokens.refreshToken(), remaining.isNegative() ? Duration.ZERO : remaining);
		response.sendRedirect(redirectPath);
	}
}
