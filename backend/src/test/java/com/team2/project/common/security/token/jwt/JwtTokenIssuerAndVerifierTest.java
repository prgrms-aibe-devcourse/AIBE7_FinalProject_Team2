package com.team2.project.common.security.token.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.common.security.token.IssuedTokens;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * JWT 발급 · 검증 · Redis 저장 왕복 (확장 단계, BE-94 · WBS 7.4.3). 실제 Redis(토큰 DB)에 붙는다.
 */
@SpringBootTest
class JwtTokenIssuerAndVerifierTest {

	@Autowired
	private JwtTokenIssuer tokenIssuer;

	@Autowired
	private JwtAccessTokenVerifier accessTokenVerifier;

	@Autowired
	private RefreshTokenStore refreshTokens;

	@Autowired
	private JwtProperties properties;

	@Test
	void issue_thenVerify_roundTrips() {
		MemberPrincipal member = MemberPrincipal.user(42);

		IssuedTokens tokens = tokenIssuer.issue(member);

		MemberPrincipal verified = accessTokenVerifier.verify(tokens.accessToken());
		assertThat(verified).isEqualTo(member);
	}

	@Test
	void issue_storesRefreshTokenInRedisWithTtl() {
		IssuedTokens tokens = tokenIssuer.issue(MemberPrincipal.user(7));

		var stored = refreshTokens.find(tokens.refreshToken());
		assertThat(stored).isPresent();
		assertThat(stored.get().memberId()).isEqualTo(7L);
		assertThat(stored.get().roles()).containsExactly(MemberPrincipal.USER);

		refreshTokens.delete(tokens.refreshToken());
	}

	@Test
	void issue_eachCallProducesDifferentRefreshToken() {
		MemberPrincipal member = MemberPrincipal.user(1);

		IssuedTokens first = tokenIssuer.issue(member);
		IssuedTokens second = tokenIssuer.issue(member);

		assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
		refreshTokens.delete(first.refreshToken());
		refreshTokens.delete(second.refreshToken());
	}

	@Test
	void verify_garbageToken_throwsInvalid() {
		assertThatThrownBy(() -> accessTokenVerifier.verify("not-a-jwt"))
			.isInstanceOf(TokenException.class)
			.extracting(e -> ((TokenException) e).reason())
			.isEqualTo(TokenException.Reason.INVALID);
	}

	@Test
	void verify_tamperedSignature_throwsInvalid() {
		IssuedTokens tokens = tokenIssuer.issue(MemberPrincipal.user(5));
		String tampered = tokens.accessToken().substring(0, tokens.accessToken().length() - 1)
			+ (tokens.accessToken().endsWith("a") ? "b" : "a");

		assertThatThrownBy(() -> accessTokenVerifier.verify(tampered))
			.isInstanceOf(TokenException.class)
			.extracting(e -> ((TokenException) e).reason())
			.isEqualTo(TokenException.Reason.INVALID);

		refreshTokens.delete(tokens.refreshToken());
	}

	@Test
	void verify_expiredToken_throwsExpired() {
		SecretKey key = Keys.hmacShaKeyFor(properties.secretBytes());
		Instant now = Instant.now();
		String expired = Jwts.builder()
			.issuer(properties.issuer())
			.subject("9")
			.claim("roles", List.of(MemberPrincipal.USER))
			.issuedAt(Date.from(now.minusSeconds(3600)))
			.expiration(Date.from(now.minusSeconds(1)))
			.signWith(key)
			.compact();

		assertThatThrownBy(() -> accessTokenVerifier.verify(expired))
			.isInstanceOf(TokenException.class)
			.extracting(e -> ((TokenException) e).reason())
			.isEqualTo(TokenException.Reason.EXPIRED);
	}

	@Test
	void verify_wrongIssuer_throwsInvalid() {
		SecretKey key = Keys.hmacShaKeyFor(properties.secretBytes());
		Instant now = Instant.now();
		String wrongIssuer = Jwts.builder()
			.issuer("someone-else")
			.subject("9")
			.claim("roles", List.of(MemberPrincipal.USER))
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plusSeconds(60)))
			.signWith(key)
			.compact();

		assertThatThrownBy(() -> accessTokenVerifier.verify(wrongIssuer))
			.isInstanceOf(TokenException.class)
			.extracting(e -> ((TokenException) e).reason())
			.isEqualTo(TokenException.Reason.INVALID);
	}
}
