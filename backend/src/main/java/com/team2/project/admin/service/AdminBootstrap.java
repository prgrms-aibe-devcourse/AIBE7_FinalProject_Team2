package com.team2.project.admin.service;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첫 관리자 계정 생성 (BE-39). 환경변수 ADMIN_BOOTSTRAP_EMAIL · ADMIN_BOOTSTRAP_PASSWORD가 둘 다 있고
 * 그 이메일의 계정이 없을 때만 한 번 만든다. 이미 있는 계정의 비밀번호는 바꾸지 않는다(환경변수로 덮어쓰기 방지).
 * 비밀번호는 코드 · 설정 파일에 두지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

	public static final int MIN_PASSWORD_LENGTH = 12;

	private final AdminAccountRepository repository;
	private final PasswordEncoder passwordEncoder;

	@Value("${admin.bootstrap.email:}")
	private String email;

	@Value("${admin.bootstrap.password:}")
	private String password;

	@Value("${admin.bootstrap.display-name:관리자}")
	private String displayName;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		createIfAbsent(email, password, displayName);
	}

	/** 만들었으면 true. 값이 비었거나 · 이미 있거나 · 비밀번호가 짧으면 만들지 않는다 */
	public boolean createIfAbsent(String rawEmail, String rawPassword, String name) {
		if (rawEmail == null || rawEmail.isBlank() || rawPassword == null || rawPassword.isBlank()) {
			return false;
		}
		String normalized = AdminAccount.normalizeEmail(rawEmail);
		if (repository.existsByEmail(normalized)) {
			return false;
		}
		if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
			log.warn("첫 관리자 계정을 만들지 않았습니다: 비밀번호는 {}자 이상이어야 합니다", MIN_PASSWORD_LENGTH);
			return false;
		}
		AdminAccount account = repository.save(
			AdminAccount.withPassword(normalized, passwordEncoder.encode(rawPassword), name));
		log.info("첫 관리자 계정을 만들었습니다: adminId={}. ADMIN_BOOTSTRAP_PASSWORD 환경변수를 지우세요", account.getId());
		return true;
	}
}
