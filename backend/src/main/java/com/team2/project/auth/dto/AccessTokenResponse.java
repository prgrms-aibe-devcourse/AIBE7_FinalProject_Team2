package com.team2.project.auth.dto;

import com.team2.project.common.security.token.IssuedTokens;
import java.time.Instant;

/** 재발급 응답. 리프레시 토큰은 쿠키로만 내려가고 응답 본문에는 담지 않는다 */
public record AccessTokenResponse(String accessToken, Instant accessTokenExpiresAt) {

	public static AccessTokenResponse of(IssuedTokens tokens) {
		return new AccessTokenResponse(tokens.accessToken(), tokens.accessTokenExpiresAt());
	}
}
