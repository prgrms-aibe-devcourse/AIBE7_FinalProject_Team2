package com.team2.project.auth.oauth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 리다이렉트 경로 검증(열린 리다이렉트 방지) 단위 테스트 (BE-62, PR #229 리뷰) */
class KakaoOAuthPropertiesTest {

	@ParameterizedTest
	@ValueSource(strings = {"/auth/callback", "/auth/callback?error=login_failed"})
	void 우리_사이트_안의_경로는_허용한다(String path) {
		assertThatCode(() -> new KakaoOAuthProperties("id", "secret", path, path)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	// 브라우저가 제어 문자를 지우면 "//evil.com"(다른 사이트)으로 읽히는 값들
	@ValueSource(strings = {"https://evil.com", "//evil.com", "/\\evil.com", "/\t/evil.com", "/\n/evil.com", "/\r/evil.com"})
	void 다른_사이트로_읽힐_수_있는_경로는_거절한다(String path) {
		assertThatThrownBy(() -> new KakaoOAuthProperties("id", "secret", path, "/auth/callback"))
			.isInstanceOf(IllegalStateException.class);
	}
}
