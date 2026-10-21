package com.team2.project.common.security.token.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.common.security.token.IssuedTokens;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 리프레시 토큰 재발급(로테이션) · 로그아웃(폐기) (확장 단계, BE-94 · WBS 7.4.3) */
@SpringBootTest
class TokenRefreshServiceTest {

	@Autowired
	private TokenRefreshService tokenRefreshService;

	@Autowired
	private JwtTokenIssuer tokenIssuer;

	@Autowired
	private RefreshTokenStore refreshTokens;

	@Test
	void reissue_rotatesRefreshToken_andKeepsMember() {
		IssuedTokens original = tokenIssuer.issue(MemberPrincipal.user(11));

		IssuedTokens reissued = tokenRefreshService.reissue(original.refreshToken());

		assertThat(reissued.refreshToken()).isNotEqualTo(original.refreshToken());
		assertThat(refreshTokens.find(original.refreshToken())).isEmpty();	// 기존 토큰은 지워졌다
		assertThat(refreshTokens.find(reissued.refreshToken())).isPresent();

		refreshTokens.delete(reissued.refreshToken());
	}

	@Test
	void reissue_unknownToken_throwsInvalid() {
		assertThatThrownBy(() -> tokenRefreshService.reissue("no-such-token"))
			.isInstanceOf(TokenException.class)
			.extracting(e -> ((TokenException) e).reason())
			.isEqualTo(TokenException.Reason.INVALID);
	}

	@Test
	void reissue_alreadyUsedToken_rejectedSecondTime() {
		// 로테이션 후 같은(탈취됐을 수 있는) 리프레시 토큰을 다시 쓰면 막혀야 한다
		IssuedTokens original = tokenIssuer.issue(MemberPrincipal.user(12));
		IssuedTokens reissued = tokenRefreshService.reissue(original.refreshToken());

		assertThatThrownBy(() -> tokenRefreshService.reissue(original.refreshToken()))
			.isInstanceOf(TokenException.class);

		refreshTokens.delete(reissued.refreshToken());
	}

	@Test
	void revoke_removesToken() {
		IssuedTokens tokens = tokenIssuer.issue(MemberPrincipal.user(13));

		tokenRefreshService.revoke(tokens.refreshToken());

		assertThat(refreshTokens.find(tokens.refreshToken())).isEmpty();
	}

	@Test
	void revoke_unknownToken_doesNotThrow() {
		tokenRefreshService.revoke("never-issued");
	}
}
