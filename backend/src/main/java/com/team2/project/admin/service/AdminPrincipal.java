package com.team2.project.admin.service;

import com.team2.project.admin.domain.AdminAccount;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 로그인한 관리자 (세션에 저장된다). 권한은 ROLE_ADMIN 하나다.
 * 세션 직렬화를 위해 엔티티가 아닌 값만 들고 다닌다.
 */
public record AdminPrincipal(Long adminId, String email, String displayName, String passwordHash, boolean enabled)
	implements UserDetails {

	@Serial
	private static final long serialVersionUID = 1L;

	public static final String ROLE = "ADMIN";

	public static AdminPrincipal from(AdminAccount account) {
		return new AdminPrincipal(account.getId(), account.getEmail(), account.getDisplayName(),
			account.getPasswordHash(), account.isEnabled());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + ROLE));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * 비밀번호 해시는 출력하지 않는다. record 기본 toString()은 모든 필드를 찍어, 로그인 직후 principal이
	 * 로그 · 예외 메시지에 찍히면 bcrypt 해시가 노출된다 (#104 리뷰, AdminLoginRequest와 같은 방식)
	 */
	@Override
	public String toString() {
		return "AdminPrincipal[adminId=" + adminId + ", email=" + email + ", displayName=" + displayName
			+ ", passwordHash=" + (passwordHash == null ? "null" : "***") + ", enabled=" + enabled + "]";
	}

	/** 세션에는 비밀번호 해시를 남기지 않는다 (로그인 확인이 끝난 뒤 이 값으로 바꿔 저장) */
	public AdminPrincipal withoutPassword() {
		return new AdminPrincipal(adminId, email, displayName, null, enabled);
	}
}
