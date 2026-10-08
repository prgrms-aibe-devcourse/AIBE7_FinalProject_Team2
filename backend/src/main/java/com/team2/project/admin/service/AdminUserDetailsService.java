package com.team2.project.admin.service;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비밀번호 로그인용 관리자 조회. 계정이 없으면 Spring Security가 "아이디 · 비밀번호 불일치"와 같은 응답으로 바꾼다 */
@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

	private final AdminAccountRepository repository;

	@Override
	@Transactional(readOnly = true)
	public AdminPrincipal loadUserByUsername(String email) {
		return repository.findByEmail(AdminAccount.normalizeEmail(email))
			.filter(account -> account.getPasswordHash() != null)	// OAuth 전용 계정은 비밀번호로 로그인할 수 없다
			.map(AdminPrincipal::from)
			.orElseThrow(() -> new UsernameNotFoundException("관리자 계정 없음"));
	}
}
