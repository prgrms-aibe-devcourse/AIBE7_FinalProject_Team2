package com.team2.project.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * 카카오 로그인 설정 (확장 단계, BE-62 · WBS 7.4.2). client-id가 있을 때만 바인딩된다(KakaoOAuthEnabled)
 *
 * @param clientId        카카오 REST API 키(환경변수 KAKAO_CLIENT_ID)
 * @param clientSecret    카카오 Client Secret(환경변수 KAKAO_CLIENT_SECRET). 카카오 콘솔에서 켜고 반드시 넣는다
 * @param successRedirect 로그인 성공 후 보낼 화면 경로. 화면은 여기서 재발급 API를 불러 액세스 토큰을 받는다
 * @param failureRedirect 로그인 실패 · 취소 후 보낼 화면 경로
 */
@ConfigurationProperties("kakao.oauth")
public record KakaoOAuthProperties(
	String clientId,
	String clientSecret,
	@DefaultValue("/auth/callback") String successRedirect,
	@DefaultValue("/auth/callback?error=login_failed") String failureRedirect
) {

	public KakaoOAuthProperties {
		if (!StringUtils.hasText(clientSecret)) {
			throw new IllegalStateException("KAKAO_CLIENT_ID가 있으면 KAKAO_CLIENT_SECRET도 넣어야 한다 (카카오 콘솔 > 보안 > Client Secret)");
		}
		requireSameOriginPath(successRedirect, "kakao.oauth.success-redirect");
		requireSameOriginPath(failureRedirect, "kakao.oauth.failure-redirect");
	}

	/** 열린 리다이렉트 방지: 우리 사이트 안의 경로만 허용한다("/..."로 시작, "//"와 "\" · 제어 문자 금지) */
	private static void requireSameOriginPath(String path, String name) {
		if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains("\\")
			// 브라우저는 탭 · 개행 같은 제어 문자를 지우고 읽어 "/\t/evil.com"이 "//evil.com"이 된다 (PR #229 리뷰)
			|| path.chars().anyMatch(c -> c < 0x20 || c == 0x7F)) {
			throw new IllegalStateException(name + "는 우리 사이트 안의 경로(/...)여야 한다");
		}
	}
}
