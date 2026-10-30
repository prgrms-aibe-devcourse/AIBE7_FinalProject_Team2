package com.team2.project.auth.controller;

import com.team2.project.auth.dto.LoginRequest;
import com.team2.project.auth.dto.LoginResponse;
import com.team2.project.auth.dto.SignupRequest;
import com.team2.project.common.security.token.IssuedTokens;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenIssuer;
import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.common.web.RefreshTokenCookies;
import com.team2.project.member.service.LocalAccountService;
import com.team2.project.member.service.LocalAccountService.LocalLoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 아이디 회원가입(M-5) · 로그인(M-6) (확장 단계, BE-113 · docs/decisions/BE-113.md)
 *
 * 성공하면 카카오 로그인(OAuthLoginSuccessHandler)과 같은 흐름이다:
 * 회원 처리 · 익명 기록 연결 → TokenIssuer.issue → 리프레시 토큰만 쿠키로 → 화면이 M-1 재발급으로 액세스 토큰을 받는다.
 * 쿠키는 회원 처리 · 토큰 발급이 모두 끝난 뒤에만 심는다(중간에 실패하면 아무 쿠키도 바뀌지 않게).
 *
 * 응답이 리프레시 쿠키를 심으므로 로그인 CSRF를 Origin 검증으로 막는다(SecurityConfig.originProtectedRequests, 결정 5).
 * 요청 횟수 제한은 Nginx가 맡는다(결정 6)
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class LocalAuthController {

	private final LocalAccountService localAccountService;
	private final TokenIssuer tokenIssuer;
	private final RefreshTokenCookies refreshTokenCookies;
	private final AnonymousIdCookie anonymousIdCookie;
	private final Clock clock;

	/** 가입하고 바로 로그인한다. 중복 아이디는 409 DUPLICATE_ID, 약관에 동의하지 않으면 400(DTO 검사, BE-133) */
	@PostMapping("/signup")
	public LoginResponse signup(@Valid @RequestBody SignupRequest body, HttpServletRequest request,
		HttpServletResponse response) {
		LocalLoginResult result = localAccountService.signup(body.id(), body.password(), body.nickname(),
			Boolean.TRUE.equals(body.agreed()), anonymousIdCookie.read(request));
		return complete(result, response);
	}

	/** 실패는 사유와 상관없이 401 INVALID_CREDENTIALS */
	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
		HttpServletResponse response) {
		LocalLoginResult result = localAccountService.login(body.id(), body.password(), anonymousIdCookie.read(request));
		return complete(result, response);
	}

	private LoginResponse complete(LocalLoginResult result, HttpServletResponse response) {
		IssuedTokens tokens = tokenIssuer.issue(MemberPrincipal.user(result.memberId()));
		result.issuedAnonymousId().ifPresent(id -> anonymousIdCookie.write(response, id));
		Duration remaining = Duration.between(clock.instant(), tokens.refreshTokenExpiresAt());
		refreshTokenCookies.write(response, tokens.refreshToken(), remaining.isNegative() ? Duration.ZERO : remaining);
		return new LoginResponse(result.nickname());
	}
}
