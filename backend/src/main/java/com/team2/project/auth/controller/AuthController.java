package com.team2.project.auth.controller;

import com.team2.project.auth.dto.AccessTokenResponse;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.security.token.IssuedTokens;
import com.team2.project.common.security.token.TokenException;
import com.team2.project.common.security.token.jwt.TokenRefreshService;
import com.team2.project.common.web.RefreshTokenCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토큰 재발급 · 로그아웃 (확장 단계, BE-94 · WBS 7.4.3)
 *
 * 둘 다 리프레시 토큰 쿠키(NLNB_RT)로만 인증한다. Authorization 헤더는 보지 않는다 —
 * 재발급을 부르는 상황은 액세스 토큰이 없거나 만료됐을 때이고, 화면도 이 요청에는 헤더를 붙이지 않는다
 * (docs/tech-stack.md 3장 "재발급 요청"). CSRF 대책은 OriginVerificationFilter(SecurityConfig)가 맡는다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final TokenRefreshService tokenRefreshService;
	private final RefreshTokenCookies refreshTokenCookies;

	/** 리프레시 토큰을 로테이션하며 새 액세스 토큰을 내준다 */
	@PostMapping("/token/reissue")
	public AccessTokenResponse reissue(HttpServletRequest request, HttpServletResponse response) {
		try {
			String refreshToken = refreshTokenCookies.read(request)
				.orElseThrow(() -> TokenException.invalid("리프레시 토큰 쿠키 없음"));
			IssuedTokens tokens = tokenRefreshService.reissue(refreshToken);
			refreshTokenCookies.write(response, tokens.refreshToken(), remaining(tokens.refreshTokenExpiresAt()));
			return AccessTokenResponse.of(tokens);
		} catch (TokenException e) {
			throw new BusinessException(e.reason().errorCode(), e.getMessage());
		}
	}

	/** 리프레시 토큰을 폐기하고 쿠키를 지운다. 쿠키가 없거나 이미 폐기됐어도 204(멱등) */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(HttpServletRequest request, HttpServletResponse response) {
		refreshTokenCookies.read(request).ifPresent(tokenRefreshService::revoke);
		refreshTokenCookies.clear(response);
	}

	private static Duration remaining(Instant expiresAt) {
		Duration remaining = Duration.between(Instant.now(), expiresAt);
		return remaining.isNegative() ? Duration.ZERO : remaining;
	}
}
