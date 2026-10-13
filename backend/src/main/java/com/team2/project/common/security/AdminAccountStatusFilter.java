package com.team2.project.common.security;

import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로그인한 관리자의 계정이 아직 있고 활성인지 요청마다 확인한다 (확장 단계, BE-67 · WBS 7.4.5)
 *
 * 세션에는 로그인 때의 정보만 들어 있어서, 로그인한 뒤 계정이 비활성 · 삭제돼도 세션이 끝날 때까지(기본 30분)
 * 관리자 API를 쓸 수 있었다. 계정이 없거나 비활성이면 세션을 끝내고 인증을 지운 뒤 **바로 401 UNAUTHORIZED로 끝낸다**.
 * CSRF 검사보다 먼저 돈다 — CSRF 토큰 없이 온 요청도 403이 아니라 401로 끝나고 세션이 남지 않게 한다(#116 리뷰).
 * 로그인 · CSRF 발급 경로는 원래대로 열려 있어야 하므로 세션만 끝내고 요청은 그대로 보낸다.
 *
 * 관리자 요청마다 기본 키 조회 한 번이 더 든다. 관리자 요청은 적어서 감수한다.
 * 스프링 빈으로 등록하지 않는다(서블릿 필터로 자동 등록되면 모든 요청에 걸린다). SecurityConfig가 관리자 체인에만 넣는다.
 */
@Slf4j
public class AdminAccountStatusFilter extends OncePerRequestFilter {

	private final AdminAccountRepository accounts;
	private final JsonSecurityErrorHandler errors;
	private final Set<String> openPaths;	// 로그인 · CSRF 발급 (permitAll)
	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	public AdminAccountStatusFilter(AdminAccountRepository accounts, JsonSecurityErrorHandler errors, Set<String> openPaths) {
		this.accounts = accounts;
		this.errors = errors;
		this.openPaths = openPaths;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		Authentication authentication = contextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AdminPrincipal admin
			&& !accounts.existsByIdAndEnabledTrue(admin.adminId())) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.invalidate();
			}
			contextHolder.clearContext();
			log.info("비활성 · 삭제된 관리자 세션 종료: adminId={}", admin.adminId());
			if (!openPaths.contains(request.getRequestURI())) {
				errors.write(response, ErrorCode.UNAUTHORIZED);
				return;
			}
		}
		chain.doFilter(request, response);
	}
}
