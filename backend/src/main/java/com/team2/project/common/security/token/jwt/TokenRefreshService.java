package com.team2.project.common.security.token.jwt;

import com.team2.project.common.security.token.IssuedTokens;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenException;
import com.team2.project.common.security.token.TokenIssuer;
import org.springframework.stereotype.Service;

/**
 * 리프레시 토큰으로 재발급 · 로그아웃(폐기) (확장 단계, BE-94 · WBS 7.4.3)
 *
 * 재발급은 리프레시 토큰을 로테이션한다(기존 토큰을 지우고 새 쌍을 발급) — 탈취된 리프레시 토큰이
 * 재사용되면 원래 주인의 다음 재발급이 실패해 알아챌 수 있다(재사용 탐지는 7.4.3 범위 밖, 추후 재검토).
 */
@Service
public class TokenRefreshService {

	private final RefreshTokenStore refreshTokens;
	private final TokenIssuer tokenIssuer;

	public TokenRefreshService(RefreshTokenStore refreshTokens, TokenIssuer tokenIssuer) {
		this.refreshTokens = refreshTokens;
		this.tokenIssuer = tokenIssuer;
	}

	/** @throws TokenException 리프레시 토큰이 없거나 이미 폐기됨(INVALID) */
	public IssuedTokens reissue(String refreshToken) {
		StoredRefreshToken stored = refreshTokens.find(refreshToken)
			.orElseThrow(() -> TokenException.invalid("리프레시 토큰이 없거나 만료됨"));
		refreshTokens.delete(refreshToken);
		MemberPrincipal member = new MemberPrincipal(stored.memberId(), stored.roles());
		return tokenIssuer.issue(member);
	}

	/** 토큰이 이미 없어도 성공으로 본다(로그아웃은 멱등해야 한다) */
	public void revoke(String refreshToken) {
		refreshTokens.delete(refreshToken);
	}
}
