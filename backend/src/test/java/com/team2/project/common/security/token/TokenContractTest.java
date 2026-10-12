package com.team2.project.common.security.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.common.exception.ErrorCode;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 보안 3분할(B · C · D)이 함께 쓰는 토큰 계약 (BE-57) */
class TokenContractTest {

	@Test
	void memberPrincipal_rolesAreImmutableAndPrefixed() {
		Set<String> roles = new HashSet<>(Set.of("USER"));
		MemberPrincipal member = new MemberPrincipal(7, roles);
		roles.add("ADMIN");	// 바깥 Set을 바꿔도 영향 없음

		assertThat(member.roles()).containsExactly("USER");
		assertThatThrownBy(() -> member.roles().add("X")).isInstanceOf(UnsupportedOperationException.class);
		assertThat(member.authorities()).extracting(Object::toString).containsExactly("ROLE_USER");
		assertThat(MemberPrincipal.user(3)).isEqualTo(new MemberPrincipal(3, Set.of(MemberPrincipal.USER)));
	}

	@Test
	void memberPrincipal_rejectsBadValues() {
		assertThatThrownBy(() -> MemberPrincipal.user(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MemberPrincipal(1, Set.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MemberPrincipal(1, Set.of("ROLE_USER"))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MemberPrincipal(1, Set.of(" "))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void issuedTokens_toStringHidesTokens() {
		Instant now = Instant.parse("2026-10-13T00:00:00Z");
		IssuedTokens tokens = new IssuedTokens("secret-access", now.plusSeconds(900), "secret-refresh", now.plusSeconds(1_209_600));

		assertThat(tokens.toString()).doesNotContain("secret-access").doesNotContain("secret-refresh").contains("***");
		assertThatThrownBy(() -> new IssuedTokens(" ", now, "r", now)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void tokenException_mapsToErrorCodes() {
		assertThat(TokenException.invalid("x").reason().errorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
		assertThat(TokenException.expired("x").reason().errorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED);
	}
}
