package com.team2.project.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * 카카오 로그인 실패 · 취소 처리 (확장 단계, BE-62). JSON 401이 아니라 화면 경로로 리다이렉트한다(브라우저 이동 경로)
 * 로그에는 오류 코드만 남긴다(인가 코드 · 토큰 · 개인정보는 남기지 않는다). state 쿠키는 저장소가 이미 지웠다
 */
@Slf4j
class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

	private final String redirectPath;

	OAuthLoginFailureHandler(String redirectPath) {
		this.redirectPath = redirectPath;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException exception) throws IOException {
		String code = exception instanceof OAuth2AuthenticationException oauth
			? oauth.getError().getErrorCode()
			: exception.getClass().getSimpleName();
		log.info("카카오 로그인 실패: {}", code);
		response.sendRedirect(redirectPath);
	}
}
