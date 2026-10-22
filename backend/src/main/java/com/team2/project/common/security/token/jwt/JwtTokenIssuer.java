package com.team2.project.common.security.token.jwt;

import com.team2.project.common.security.token.IssuedTokens;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenIssuer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 액세스 토큰 발급 + 리프레시 토큰 발급 · Redis 저장 (확장 단계, BE-94 · WBS 7.4.3)
 *
 * 리프레시 토큰은 JWT가 아니라 SecureRandom으로 만든 불투명한 값이다. 서버만 들고 있는 Redis 저장소와
 * 대조해야만 의미가 있어, 액세스 토큰처럼 자체 검증 가능한 형식일 필요가 없다(탈취돼도 폐기·로테이션이 즉시 먹힌다).
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

	/** 256비트 = HS256 서명에 필요한 최소 키 길이 이상의 무작위성 */
	private static final int REFRESH_TOKEN_BYTES = 32;

	private final JwtProperties properties;
	private final SecretKey signingKey;
	private final RefreshTokenStore refreshTokens;
	private final SecureRandom random = new SecureRandom();

	public JwtTokenIssuer(JwtProperties properties, RefreshTokenStore refreshTokens) {
		this.properties = properties;
		this.signingKey = Keys.hmacShaKeyFor(properties.secretBytes());
		this.refreshTokens = refreshTokens;
	}

	@Override
	public IssuedTokens issue(MemberPrincipal member) {
		Instant now = Instant.now();
		Instant accessExpiresAt = now.plus(properties.accessTokenTtl());
		String accessToken = Jwts.builder()
			.issuer(properties.issuer())
			.subject(String.valueOf(member.memberId()))
			.claim("roles", member.sortedRoles())
			.issuedAt(java.util.Date.from(now))
			.expiration(java.util.Date.from(accessExpiresAt))
			.signWith(signingKey)
			.compact();

		String refreshToken = newOpaqueToken();
		Instant refreshExpiresAt = now.plus(properties.refreshTokenTtl());
		refreshTokens.save(refreshToken, member.memberId(), member.roles(), properties.refreshTokenTtl());

		return new IssuedTokens(accessToken, accessExpiresAt, refreshToken, refreshExpiresAt);
	}

	private String newOpaqueToken() {
		byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
