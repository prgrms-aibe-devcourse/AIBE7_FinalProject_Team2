package com.team2.project.common.security;

import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.admin.service.AdminUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 인증 · 권한 (확장 단계, BE-39)
 *
 * - **사용자 API는 지금처럼 인증 없이 동작한다.** 익명 ID 쿠키로 구분하고 세션 · CSRF를 쓰지 않는다
 * - `/api/v1/admin/**`만 관리자(ROLE_ADMIN)를 요구한다. 로그인 · CSRF 토큰 발급만 예외
 * - 관리자 로그인은 세션(쿠키 NLNB_ADMIN_SESSION). 폼 로그인 · HTTP Basic · 기본 로그아웃은 쓰지 않고 JSON API로 한다
 * - 관리자 API의 상태 변경 요청(POST 등)은 CSRF 토큰(쿠키 XSRF-TOKEN → 헤더 X-XSRF-TOKEN)을 요구한다. 로그인도 포함
 * - 막힌 요청은 공통 에러 응답(401 UNAUTHORIZED · 403 FORBIDDEN)으로 바꾼다
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	public static final String ADMIN_PATTERN = "/api/v1/admin/**";
	public static final String LOGIN_PATH = "/api/v1/admin/auth/login";
	public static final String CSRF_PATH = "/api/v1/admin/auth/csrf";
	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

	@Value("${server.servlet.session.cookie.secure:true}")
	private boolean secureCookie;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JsonSecurityErrorHandler errors) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(LOGIN_PATH, CSRF_PATH).permitAll()
				.requestMatchers(ADMIN_PATTERN).hasRole(AdminPrincipal.ROLE)
				.anyRequest().permitAll())
			// csrf.spa()는 모든 응답에 토큰 쿠키를 심어 사용자 API에도 쿠키가 생긴다. 토큰은 지연 생성해
			// 관리자 CSRF 발급 API(/csrf)를 부르거나 관리자 상태 변경 요청을 확인할 때만 쿠키를 다룬다
			.csrf(csrf -> csrf
				.csrfTokenRepository(csrfTokenRepository())
				.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
				.requireCsrfProtectionMatcher(adminStateChanging()))
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
			.sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()));
		return http.build();
	}

	/** 쿠키 XSRF-TOKEN(화면 JS가 읽어 헤더 X-XSRF-TOKEN에 담는다). 관리자 세션 쿠키와 같은 Secure 설정을 따른다 */
	private CookieCsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer(cookie -> cookie.secure(secureCookie).sameSite("Lax"));
		return repository;
	}

	/** CSRF를 확인할 요청: 관리자 API의 상태 변경 요청만. 사용자 API(익명 쿠키)는 지금처럼 확인하지 않는다 */
	private static RequestMatcher adminStateChanging() {
		RequestMatcher admin = PathPatternRequestMatcher.withDefaults().matcher(ADMIN_PATTERN);
		return (HttpServletRequest request) -> !SAFE_METHODS.contains(request.getMethod()) && admin.matches(request);
	}

	/** 비밀번호 해시. {bcrypt} 접두어가 붙어 나중에 알고리즘을 바꿔도 기존 해시를 읽는다 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	/** 관리자 비밀번호 로그인. 계정 없음 · 비활성 · 비밀번호 불일치는 모두 같은 실패로 처리한다(로그인 API) */
	@Bean
	public AuthenticationManager authenticationManager(AdminUserDetailsService userDetailsService,
		PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	public SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}
}
