package com.team2.project.common.security.token;

import java.time.Instant;
import java.util.Objects;

/**
 * 발급된 토큰 한 쌍과 만료 시각 (확장 단계, BE-57)
 *
 * 토큰 값은 비밀이다. {@link #toString()}은 값을 가리고, 로그 · 예외 메시지에 넣지 않는다.
 */
public record IssuedTokens(String accessToken, Instant accessTokenExpiresAt, String refreshToken,
	Instant refreshTokenExpiresAt) {

	public IssuedTokens {
		requireText(accessToken, "accessToken");
		requireText(refreshToken, "refreshToken");
		Objects.requireNonNull(accessTokenExpiresAt, "accessTokenExpiresAt");
		Objects.requireNonNull(refreshTokenExpiresAt, "refreshTokenExpiresAt");
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + "가 비었다");
		}
	}

	@Override
	public String toString() {
		return "IssuedTokens[accessToken=***, accessTokenExpiresAt=" + accessTokenExpiresAt
			+ ", refreshToken=***, refreshTokenExpiresAt=" + refreshTokenExpiresAt + "]";
	}
}
