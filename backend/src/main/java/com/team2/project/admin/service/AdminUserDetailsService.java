package com.team2.project.admin.service;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 로그인용 관리자 조회. 계정이 없으면 Spring Security가 "아이디 · 비밀번호 불일치"와 같은 응답으로 바꾼다.
 *
 * 비활성 계정 · OAuth 전용 계정도 "계정 없음"과 똑같이 처리한다 (BE-67). 계정이 없을 때 DaoAuthenticationProvider는
 * 가짜 해시로 비밀번호 확인을 한 번 해서 응답 시간을 맞추는데, 비활성 계정을 그대로 돌려주면 비밀번호 확인 전에
 * "비활성"으로 바로 거절돼 응답이 빨라진다. 그 시간 차이로 "가입된 이메일"을 알아낼 수 없게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService, UserDetailsPasswordService {

	private final AdminAccountRepository repository;

	@Override
	@Transactional(readOnly = true)
	public AdminPrincipal loadUserByUsername(String email) {
		return repository.findByEmail(AdminAccount.normalizeEmail(email))
			.filter(account -> account.getPasswordHash() != null)	// OAuth 전용 계정은 비밀번호로 로그인할 수 없다
			.filter(AdminAccount::isEnabled)	// 비활성도 계정 없음과 같은 경로(가짜 비밀번호 확인)로
			.map(AdminPrincipal::from)
			.orElseThrow(() -> new UsernameNotFoundException("관리자 계정 없음"));
	}

	/**
	 * 로그인 성공 직후 호출된다(DaoAuthenticationProvider). 옛 해시({bcrypt})를 새 인코더({argon2})로
	 * 바꿔 저장해, 로그인할 때마다 조금씩 전체 계정을 전환한다 (BE-90)
	 */
	@Override
	@Transactional
	public UserDetails updatePassword(UserDetails user, String newEncodedPassword) {
		AdminPrincipal principal = (AdminPrincipal) user;
		repository.findById(principal.adminId()).ifPresent(account -> account.changePasswordHash(newEncodedPassword));
		return new AdminPrincipal(principal.adminId(), principal.email(), principal.displayName(),
			newEncodedPassword, principal.enabled());
	}
}
