package com.team2.project.admin.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 관리자 계정 (확장 단계, BE-39). 관리자 후검수 API(/api/v1/admin/**)를 쓰는 사람.
 * 사용자는 익명 ID 쿠키로만 구분하므로 이 계정과 관계없다.
 */
@Entity
@Table(name = "admin_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String email;			// 소문자로 정규화해 저장 (DB CHECK)

	private String passwordHash;	// BCrypt. OAuth로만 로그인하는 관리자(BE-40)는 없을 수 있다

	private String displayName;

	private boolean enabled;

	@CreationTimestamp
	private Instant createdAt;

	private Instant lastLoginAt;

	private AdminAccount(String email, String passwordHash, String displayName) {
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.enabled = true;
	}

	/** 비밀번호로 로그인하는 관리자. passwordHash는 이미 인코딩한 값이다 */
	public static AdminAccount withPassword(String email, String passwordHash, String displayName) {
		return new AdminAccount(email, passwordHash, displayName);
	}

	public void recordLogin(Instant at) {
		this.lastLoginAt = at;
	}

	/** 로그인 · 조회에 쓰는 이메일 형태 (앞뒤 공백 제거 · 소문자) */
	public static String normalizeEmail(String email) {
		return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}
}
