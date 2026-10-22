package com.team2.project.common.security.token.jwt;

import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * JWT 발급 · 검증 설정 (확장 단계, BE-94 · WBS 7.4.3)
 *
 * @param secret   HMAC-SHA256 서명 키. Base64 인코딩 문자열로 받는다(환경변수 JWT_SECRET). 코드 · 저장소에 두지 않는다
 * @param issuer   액세스 토큰 발급자(iss claim)
 * @param accessTokenTtl 액세스 토큰 유효 기간
 * @param refreshTokenTtl 리프레시 토큰 유효 기간(Redis TTL과 동일하게 맞춘다)
 */
@ConfigurationProperties("jwt")
public record JwtProperties(
	String secret,
	@DefaultValue("lawnambul") String issuer,
	@DefaultValue("15m") Duration accessTokenTtl,
	@DefaultValue("14d") Duration refreshTokenTtl
) {

	public JwtProperties {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("jwt.secret이 설정되지 않았다 (환경변수 JWT_SECRET)");
		}
		byte[] decoded = Base64.getDecoder().decode(secret);
		if (decoded.length < 32) {
			throw new IllegalStateException("jwt.secret은 Base64로 디코딩했을 때 32바이트(HS256) 이상이어야 한다");
		}
	}

	public byte[] secretBytes() {
		return Base64.getDecoder().decode(secret);
	}
}
