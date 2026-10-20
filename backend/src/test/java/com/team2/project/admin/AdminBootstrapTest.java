package com.team2.project.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.admin.service.AdminBootstrap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 첫 관리자 계정 생성 (BE-39) */
@SpringBootTest
class AdminBootstrapTest {

	@Autowired
	private AdminBootstrap bootstrap;

	@Autowired
	private AdminAccountRepository repository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private final String email = "Boot-" + UUID.randomUUID() + "@Example.com";

	@AfterEach
	void delete() {
		repository.findByEmail(AdminAccount.normalizeEmail(email)).ifPresent(repository::delete);
	}

	@Test
	void createIfAbsent_newEmail_createsHashedNormalized() {
		assertThat(bootstrap.createIfAbsent(email, "long-enough-password", "관리자")).isTrue();

		AdminAccount account = repository.findByEmail(AdminAccount.normalizeEmail(email)).orElseThrow();
		assertThat(account.getEmail()).isEqualTo(email.toLowerCase());
		assertThat(account.getPasswordHash()).startsWith("{argon2}$argon2id$").doesNotContain("long-enough-password");
		assertThat(passwordEncoder.matches("long-enough-password", account.getPasswordHash())).isTrue();
		assertThat(account.isEnabled()).isTrue();
	}

	@Test
	@DisplayName("새 해시는 Argon2id로 만들고, 기존 BCrypt 해시도 검증한다 (BE-90)")
	void passwordEncoder_argon2ByDefault_bcryptStillMatches() {
		String hash = passwordEncoder.encode("long-enough-password");
		assertThat(hash).startsWith("{argon2}$argon2id$");
		assertThat(passwordEncoder.matches("long-enough-password", hash)).isTrue();

		String legacy = "{bcrypt}" + new BCryptPasswordEncoder().encode("legacy-password");
		assertThat(passwordEncoder.matches("legacy-password", legacy)).isTrue();
	}

	@Test
	void createIfAbsent_existing_doesNotOverwritePassword() {
		bootstrap.createIfAbsent(email, "long-enough-password", "관리자");

		assertThat(bootstrap.createIfAbsent(email, "another-long-password", "관리자")).isFalse();
		String hash = repository.findByEmail(AdminAccount.normalizeEmail(email)).orElseThrow().getPasswordHash();
		assertThat(passwordEncoder.matches("long-enough-password", hash)).isTrue();
	}

	@Test
	void createIfAbsent_blankOrShort_skips() {
		assertThat(bootstrap.createIfAbsent("", "long-enough-password", "관리자")).isFalse();
		assertThat(bootstrap.createIfAbsent(email, "", "관리자")).isFalse();
		assertThat(bootstrap.createIfAbsent(email, "short", "관리자")).isFalse();
		assertThat(repository.existsByEmail(AdminAccount.normalizeEmail(email))).isFalse();
	}
}
