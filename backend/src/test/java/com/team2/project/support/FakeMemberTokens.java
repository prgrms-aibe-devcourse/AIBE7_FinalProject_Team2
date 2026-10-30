package com.team2.project.support;

import com.team2.project.common.security.token.AccessTokenVerifier;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 시험용 토큰: "member-{회원 ID}" → 그 회원으로 인증 (BE-140). {@code @Import(FakeMemberTokens.class)}로 쓴다 */
@TestConfiguration
public class FakeMemberTokens {

	@Bean
	@Primary
	AccessTokenVerifier fakeAccessTokenVerifier() {
		return token -> {
			if (token.startsWith("member-")) {
				return MemberPrincipal.user(Long.parseLong(token.substring("member-".length())));
			}
			throw TokenException.invalid("알 수 없는 토큰");
		};
	}

	public static String bearer(long memberId) {
		return "Bearer member-" + memberId;
	}
}
