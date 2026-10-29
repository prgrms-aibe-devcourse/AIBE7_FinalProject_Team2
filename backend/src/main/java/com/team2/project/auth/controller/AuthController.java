package com.team2.project.auth.controller;

import com.team2.project.auth.dto.AccessTokenResponse;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.security.token.IssuedTokens;
import com.team2.project.common.security.token.TokenException;
import com.team2.project.common.security.token.jwt.TokenRefreshService;
import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.common.web.RefreshTokenCookies;
import com.team2.project.member.service.AnonymousLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 로그아웃은 익명 ID 쿠키(NLNB_AID)도 읽지만 인증에 쓰지 않는다 — 회원에 연결된 값이면 지우기 위해서만 본다(BE-137)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final TokenRefreshService tokenRefreshService;
	private final RefreshTokenCookies refreshTokenCookies;
	private final AnonymousIdCookie anonymousIdCookie;
	private final AnonymousLinkService anonymousLinkService;

	/**
	 * 리프레시 토큰을 로테이션하며 새 액세스 토큰을 내준다.
	 * 실패(쿠키 없음 · 모르는 값 · 이미 로테이션 · 로그아웃된 값)하면 쿠키도 함께 지운다 — 죽은 쿠키를 남겨 두면
	 * 화면이 실패하는 재발급을 계속 반복할 수 있다(PR #218 리뷰, 정민석)
	 */
	@PostMapping("/token/reissue")
	public AccessTokenResponse reissue(HttpServletRequest request, HttpServletResponse response) {
		try {
			String refreshToken = refreshTokenCookies.read(request)
				.orElseThrow(() -> TokenException.invalid("리프레시 토큰 쿠키 없음"));
			IssuedTokens tokens = tokenRefreshService.reissue(refreshToken);
			refreshTokenCookies.write(response, tokens.refreshToken(), remaining(tokens.refreshTokenExpiresAt()));
			return AccessTokenResponse.of(tokens);
		} catch (TokenException e) {
			refreshTokenCookies.clear(response);
			throw new BusinessException(e.reason().errorCode(), e.getMessage());
		}
	}

	/**
	 * 리프레시 토큰을 폐기하고 쿠키를 지운다. 쿠키가 없거나 이미 폐기됐어도 204(멱등).
	 * 익명 ID가 회원에 연결되어 있으면 그 쿠키도 지운다 (BE-137): 로그인 때 회원에 연결한 익명 ID가 남으면
	 * 로그아웃 뒤 같은 브라우저(공용 PC)의 다음 사람 체험 · 판결이 그 회원 계정으로 쌓인다.
	 * 새 익명 ID는 여기서 발급하지 않는다 — 다음 행동(체험 시작 등) 때 발급된다(BE-97 "행동했을 때만 발급").
	 * 연결 안 된(손님) 익명 ID는 지우지 않는다. 이전 기록은 회원 계정에 그대로 남는다
	 */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(HttpServletRequest request, HttpServletResponse response) {
		refreshTokenCookies.read(request).ifPresent(tokenRefreshService::revoke);
		refreshTokenCookies.clear(response);
		if (shouldClearAnonymousId(anonymousIdCookie.read(request))) {
			anonymousIdCookie.clear(response);
		}
	}

	/**
	 * 로그아웃 때 익명 ID 쿠키를 지울지. 회원에 연결된 값이면 지운다.
	 * 연결 여부를 확인하지 못하면(DB 오류 · 연결 풀 고갈 등 어떤 실행 중 예외든) 지운다 — 남겨 두면 다음 사람 기록이
	 * 회원 계정에 쌓일 수 있고, 손님 값이었다면 다음 행동 때 새 익명 ID를 받는 것으로 끝난다.
	 * 로그아웃 자체는 실패시키지 않는다(리프레시는 이미 폐기됨). 그래서 DB 계열만이 아니라 RuntimeException 전체를 받는다 (PR #340 리뷰)
	 */
	private boolean shouldClearAnonymousId(Optional<UUID> anonymousId) {
		if (anonymousId.isEmpty()) {
			return false;
		}
		try {
			return anonymousLinkService.isLinkedToMember(anonymousId);
		} catch (RuntimeException e) {
			// 예상하지 못한 예외(코드 오류 등)도 여기서 삼키므로 종류와 스택을 남겨 원인을 찾을 수 있게 한다
			log.warn("로그아웃 중 익명 ID 연결 확인 실패 — 안전하게 쿠키를 지운다 ({})", e.getClass().getSimpleName(), e);
			return true;
		}
	}

	private static Duration remaining(Instant expiresAt) {
		Duration remaining = Duration.between(Instant.now(), expiresAt);
		return remaining.isNegative() ? Duration.ZERO : remaining;
	}
}
