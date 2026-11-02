package com.team2.project.auth.controller;

import com.team2.project.auth.dto.LoginResponse;
import com.team2.project.auth.oauth.KakaoOAuthEnabled;
import com.team2.project.auth.oauth.KakaoSignupPending;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.security.token.IssuedTokens;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenIssuer;
import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.common.web.RefreshTokenCookies;
import com.team2.project.member.domain.AuthProvider;
import com.team2.project.member.domain.Nickname;
import com.team2.project.member.dto.TermsAgreementRequest;
import com.team2.project.member.service.MemberLoginService;
import com.team2.project.member.service.MemberLoginService.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카카오 첫 가입의 약관 동의 (확장 단계, BE-145, API 명세 M-12 · M-13, docs/decisions/BE-145.md)
 *
 * 처음 온 카카오 계정은 카카오 콜백(M-4)에서 회원이 만들어지지 않고 "가입 대기"({@link KakaoSignupPending})만 생긴다.
 * - M-12: 약관에 동의하면 그때 회원과 동의 기록을 한 번에 만들고 로그인한다. 응답은 아이디 가입(M-5)과 같은 흐름이다 —
 *   리프레시 토큰만 쿠키로 심고, 화면이 M-1 재발급으로 액세스 토큰을 받는다
 * - M-13: 동의하지 않고 닫으면 가입 대기를 지운다. DB에는 아무것도 남지 않는다
 *
 * 응답이 리프레시 쿠키를 심으므로 로그인 CSRF를 Origin 검증으로 막는다(SecurityConfig.originProtectedRequests, M-5와 같다).
 * 카카오 로그인이 꺼져 있으면 이 경로도 없다
 */
@RestController
@RequestMapping(KakaoSignupPending.PATH)
@Conditional(KakaoOAuthEnabled.class)
@RequiredArgsConstructor
public class KakaoSignupController {

	private final KakaoSignupPending signupPending;
	private final MemberLoginService memberLoginService;
	private final TokenIssuer tokenIssuer;
	private final RefreshTokenCookies refreshTokenCookies;
	private final AnonymousIdCookie anonymousIdCookie;
	private final Clock clock;

	/**
	 * 동의하고 가입한다. 동의 검사(400)가 먼저라 동의하지 않은 요청은 가입 대기를 쓰지 않는다.
	 * 가입 대기가 없거나 지났거나 이미 썼으면 409 KAKAO_SIGNUP_EXPIRED — 카카오 로그인부터 다시 한다.
	 * 가입 대기를 꺼낸 뒤 DB · Redis 오류로 실패하면 그 대기는 이미 쓰인 것이라 카카오 로그인부터 다시 한다(보안상 되살리지 않는다)
	 */
	@PostMapping
	public LoginResponse signup(@Valid @RequestBody TermsAgreementRequest body, HttpServletRequest request,
		HttpServletResponse response) {
		String kakaoUserId = signupPending.consume(request, response)
			.orElseThrow(() -> new BusinessException(ErrorCode.KAKAO_SIGNUP_EXPIRED));
		LoginResult result = memberLoginService.signupWithTerms(AuthProvider.KAKAO, kakaoUserId,
			anonymousIdCookie.read(request));
		IssuedTokens tokens = tokenIssuer.issue(MemberPrincipal.user(result.memberId()));
		// 쿠키는 회원 처리 · 토큰 발급이 모두 끝난 뒤에만 심는다(중간에 실패하면 로그인 쿠키가 바뀌지 않게)
		result.issuedAnonymousId().ifPresent(id -> anonymousIdCookie.write(response, id));
		Duration remaining = Duration.between(clock.instant(), tokens.refreshTokenExpiresAt());
		refreshTokenCookies.write(response, tokens.refreshToken(), remaining.isNegative() ? Duration.ZERO : remaining);
		// 카카오 회원은 가입 때 닉네임이 없다 — 화면에는 "방청객 {회원 ID}"로 보이고 M-7로 나중에 정한다
		return new LoginResponse(Nickname.displayName(result.memberId(), null));
	}

	/** 동의하지 않고 닫는다 — 204. 가입 대기가 이미 없어도 같다 */
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancel(HttpServletRequest request, HttpServletResponse response) {
		signupPending.discard(request, response);
	}
}
