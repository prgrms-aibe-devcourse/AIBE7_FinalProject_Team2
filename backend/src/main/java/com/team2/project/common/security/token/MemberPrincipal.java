package com.team2.project.common.security.token;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 토큰으로 확인된 회원 (확장 단계, BE-57 · WBS 7.4.1)
 *
 * - {@link AccessTokenVerifier}가 액세스 토큰을 검증해 돌려주고, 사용자 체인의 Bearer 필터가 SecurityContext에 넣는다
 * - {@link TokenIssuer}에 넘겨 토큰을 발급받는다 (카카오 로그인 성공 처리, WBS 7.4.2)
 * - 권한은 접두어 없이 적는다(예: {@code USER}). Spring Security 권한은 {@link #authorities()}가 {@code ROLE_USER}로 바꾼다
 * - 회원 테이블 · ID 체계는 카카오 로그인(7.4.2)에서 정한다. 여기서는 양수 ID만 요구한다
 */
public record MemberPrincipal(long memberId, Set<String> roles) {

	/** 일반 회원 권한 */
	public static final String USER = "USER";

	private static final String ROLE_PREFIX = "ROLE_";

	public MemberPrincipal {
		if (memberId <= 0) {
			throw new IllegalArgumentException("memberId는 양수여야 한다");
		}
		Objects.requireNonNull(roles, "roles");
		roles = Set.copyOf(roles);	// 바꿀 수 없게 복사한다(null 원소는 여기서 거절된다)
		if (roles.isEmpty()) {
			throw new IllegalArgumentException("권한이 하나 이상 있어야 한다");
		}
		for (String role : roles) {
			if (role.isBlank() || role.startsWith(ROLE_PREFIX)) {
				throw new IllegalArgumentException("권한은 접두어(ROLE_) 없이 적는다: " + role);
			}
		}
	}

	/** 일반 회원 하나 */
	public static MemberPrincipal user(long memberId) {
		return new MemberPrincipal(memberId, Set.of(USER));
	}

	/** Spring Security 권한 (ROLE_ 접두어를 붙인다) */
	public Collection<? extends GrantedAuthority> authorities() {
		return roles.stream().sorted().map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role)).toList();
	}

	public List<String> sortedRoles() {
		return roles.stream().sorted().toList();
	}
}
