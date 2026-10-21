package com.team2.project.common.security.token.jwt;

import com.team2.project.common.security.token.AccessTokenVerifier;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 액세스 토큰 검증 (확장 단계, BE-94 · WBS 7.4.3)
 *
 * 서명 · 만료 · 발급자만 본다(블랙리스트는 두지 않는다 — 액세스 토큰은 짧게 살고(기본 15분), 폐기는
 * 리프레시 토큰 쪽(RefreshTokenStore)에서 막는다. 이미 나간 액세스 토큰은 자연 만료를 기다린다).
 */
@Component
public class JwtAccessTokenVerifier implements AccessTokenVerifier {

	private final JwtProperties properties;
	private final SecretKey signingKey;

	public JwtAccessTokenVerifier(JwtProperties properties) {
		this.properties = properties;
		this.signingKey = Keys.hmacShaKeyFor(properties.secretBytes());
	}

	@Override
	public MemberPrincipal verify(String accessToken) {
		Claims claims;
		try {
			claims = Jwts.parser()
				.verifyWith(signingKey)
				.requireIssuer(properties.issuer())
				.build()
				.parseSignedClaims(accessToken)
				.getPayload();
		} catch (ExpiredJwtException e) {
			throw TokenException.expired("액세스 토큰 만료");
		} catch (JwtException | IllegalArgumentException e) {
			throw TokenException.invalid("액세스 토큰 형식 · 서명 오류");
		}

		long memberId;
		try {
			memberId = Long.parseLong(claims.getSubject());
		} catch (NumberFormatException e) {
			throw TokenException.invalid("액세스 토큰 subject 형식 오류");
		}

		List<?> rawRoles = claims.get("roles", List.class);
		if (rawRoles == null || rawRoles.isEmpty()) {
			throw TokenException.invalid("액세스 토큰에 권한 정보가 없음");
		}
		Set<String> roles = rawRoles.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());

		try {
			return new MemberPrincipal(memberId, roles);
		} catch (IllegalArgumentException e) {
			throw TokenException.invalid("액세스 토큰 claim 값 오류");
		}
	}
}
