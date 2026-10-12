package com.team2.project.admin.service;

import com.team2.project.admin.dto.AdminLoginRequest;
import com.team2.project.admin.dto.AdminMeResponse;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 로그인 · 로그아웃 (확장 단계, BE-39)
 * 로그인에 성공하면 세션에 인증 정보를 저장한다. 실패 사유(계정 없음 · 비활성 · 비밀번호 불일치)는 구분하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final AdminAccountRepository accountRepository;
	private final Clock clock;
	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	@Transactional
	public AdminMeResponse login(AdminLoginRequest login, HttpServletRequest request, HttpServletResponse response) {
		AdminPrincipal principal = authenticate(login);
		accountRepository.findById(principal.adminId()).ifPresent(account -> account.recordLogin(clock.instant()));

		// 로그인 전에 받은 세션이 있으면 ID를 바꿔 세션 고정 공격을 막는다
		HttpSession existing = request.getSession(false);
		if (existing != null) {
			request.changeSessionId();
		}
		AdminPrincipal sessionPrincipal = principal.withoutPassword();
		SecurityContext context = contextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
			sessionPrincipal, null, sessionPrincipal.getAuthorities()));
		contextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		log.info("관리자 로그인: adminId={}", principal.adminId());
		return AdminMeResponse.from(sessionPrincipal);
	}

	public void logout(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		contextHolder.clearContext();
	}

	private AdminPrincipal authenticate(AdminLoginRequest login) {
		try {
			return (AdminPrincipal) authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(login.email(), login.password())).getPrincipal();
		} catch (AuthenticationException e) {
			log.info("관리자 로그인 실패 ({})", e.getClass().getSimpleName());
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
	}
}
