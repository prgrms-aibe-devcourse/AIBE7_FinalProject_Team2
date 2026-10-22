package com.team2.project.common.security;

import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.admin.service.AdminUserDetailsService;
import com.team2.project.common.security.token.AccessTokenVerifier;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 인증 · 권한. 출입구(필터 체인)를 나눈다 (관리자 BE-39, 체인 분리 BE-57 · WBS 7.4.1, 카카오 로그인 체인 BE-62)
 *
 * 순서: ① 관리자 @Order(1) → (카카오 로그인 @Order(2), auth.oauth.KakaoOAuthConfig) → ② 사용자 @Order(3).
 * 카카오 로그인 체인은 {@code /api/v1/auth/oauth2/**}만 맡고, KAKAO_CLIENT_ID가 없으면 만들어지지 않는다
 *
 * ① 관리자 체인 {@code /api/v1/admin/**} — 세션 + CSRF (BE-39)
 * - ROLE_ADMIN만. 로그인 · CSRF 토큰 발급만 예외. 없는 관리자 경로도 로그인 전이면 401
 * - 로그인은 세션(쿠키 NLNB_ADMIN_SESSION). 폼 로그인 · HTTP Basic · 기본 로그아웃은 쓰지 않고 JSON API로 한다
 * - 상태 변경 요청(POST 등, 로그인 포함)은 CSRF 토큰(쿠키 XSRF-TOKEN → 헤더 X-XSRF-TOKEN)을 요구한다
 * - Bearer 토큰은 보지 않는다(회원 토큰으로 관리자 API에 들어올 수 없다)
 * - 요청마다 세션의 관리자 계정이 아직 있고 활성인지 확인한다(AdminAccountStatusFilter, BE-67). 로그인하면 CSRF 토큰을 바꾼다
 *
 * ② 사용자 체인 (그 밖의 모든 요청) — 무상태 + Bearer 토큰 (BE-57)
 * - 세션을 만들지도 읽지도 않는다. 브라우저에 관리자 세션 쿠키가 있어도 사용자 API에서는 관리자로 보지 않는다
 *   (RequestAttributeSecurityContextRepository: 인증 정보는 그 요청 안에서만)
 * - 익명 ID 쿠키(NLNB_AID)는 지금처럼 컨트롤러가 읽는다. CSRF 토큰은 쓰지 않는다(익명 쿠키는 SameSite=Lax로 받아들인 위험, 기술 스택 3장)
 * - Authorization: Bearer가 있으면 BearerTokenAuthenticationFilter가 검증한다. 잘못됐거나 만료면 401(경로가 permitAll이어도)
 * - 지금은 모두 permitAll. 회원 전용 API를 만들 때 그 경로에 authenticated()를 추가한다
 *
 * 공통: 막힌 요청은 공통 에러 응답(JsonSecurityErrorHandler). 보안 헤더는 Spring Security 기본값 + Referrer-Policy.
 * CORS는 설정하지 않는다(프론트와 API가 같은 출처: 운영 Nginx /api 프록시, 로컬 Vite 프록시. API 명세 6장 #2).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	public static final String ADMIN_PATTERN = "/api/v1/admin/**";
	public static final String LOGIN_PATH = "/api/v1/admin/auth/login";
	public static final String CSRF_PATH = "/api/v1/admin/auth/csrf";
	/** 리프레시 토큰 쿠키로만 인증하는 상태 변경 요청 — Origin 검증 대상 (BE-94 · WBS 7.4.3) */
	public static final String REISSUE_PATH = "/api/v1/auth/token/reissue";
	public static final String LOGOUT_PATH = "/api/v1/auth/logout";
	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
	private static final ReferrerPolicy REFERRER_POLICY = ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN;

	@Value("${server.servlet.session.cookie.secure:true}")
	private boolean secureCookie;

	/** ① 관리자 체인. 먼저 검사한다(@Order(1)) */
	@Bean
	@Order(1)
	public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http, JsonSecurityErrorHandler errors,
		SecurityContextRepository adminContextRepository, CsrfTokenRepository adminCsrfTokenRepository,
		AdminAccountRepository adminAccounts) throws Exception {
		http
			.securityMatcher(ADMIN_PATTERN)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(LOGIN_PATH, CSRF_PATH).permitAll()
				.anyRequest().hasRole(AdminPrincipal.ROLE))
			.securityContext(context -> context.securityContextRepository(adminContextRepository))
			// csrf.spa()는 모든 응답에 토큰 쿠키를 심는다. 토큰은 지연 생성해 관리자 CSRF 발급 API(/csrf)를 부르거나
			// 관리자 상태 변경 요청을 확인할 때만 쿠키를 다룬다
			.csrf(csrf -> csrf
				.csrfTokenRepository(adminCsrfTokenRepository)
				.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
				.requireCsrfProtectionMatcher(stateChanging()))
			.headers(headers -> headers.referrerPolicy(referrer -> referrer.policy(REFERRER_POLICY)))
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
			.sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
			// 세션을 불러온 뒤 · CSRF 검사 전에 계정 상태를 확인한다. 비활성 · 삭제면 세션을 끝내고 바로 401 (#116 리뷰)
			.addFilterBefore(new AdminAccountStatusFilter(adminAccounts, errors, Set.of(LOGIN_PATH, CSRF_PATH)),
				CsrfFilter.class);
		return http.build();
	}

	/** ② 사용자 체인. 관리자 · 카카오 로그인 경로가 아닌 모든 요청(@Order(3), securityMatcher 없음 — 맨 마지막에 둔다) */
	@Bean
	@Order(3)
	public SecurityFilterChain userSecurityFilterChain(HttpSecurity http, JsonSecurityErrorHandler errors,
		ObjectProvider<AccessTokenVerifier> accessTokenVerifiers) throws Exception {
		SecurityContextRepository requestOnly = new RequestAttributeSecurityContextRepository();
		http
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.securityContext(context -> context.securityContextRepository(requestOnly))
			.csrf(AbstractHttpConfigurer::disable)
			.headers(headers -> headers.referrerPolicy(referrer -> referrer.policy(REFERRER_POLICY)))
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
			// SecurityContext를 불러온 뒤 · 익명 인증 전에 Bearer 토큰을 확인한다
			.addFilterBefore(new BearerTokenAuthenticationFilter(accessTokenVerifiers, errors, requestOnly),
				AnonymousAuthenticationFilter.class)
			// 재발급 · 로그아웃(리프레시 토큰 쿠키로만 인증)은 Origin 검증으로 CSRF를 막는다 (BE-94, C 결정)
			// RequestMatcher로 판정한다 — getRequestURI() 원시 비교는 context path 포함 · 미디코딩이라 우회될 수 있다(PR #218 리뷰)
			.addFilterBefore(new OriginVerificationFilter(originProtectedRequests(), errors),
				AnonymousAuthenticationFilter.class);
		return http.build();
	}

	/**
	 * 쿠키 XSRF-TOKEN(화면 JS가 읽어 헤더 X-XSRF-TOKEN에 담는다). 관리자 세션 쿠키와 같은 Secure 설정을 따른다.
	 * 로그인 서비스가 로그인 성공 때 토큰을 바꾸려고 같은 저장소를 쓰므로 빈으로 둔다 (BE-67)
	 */
	@Bean
	public CsrfTokenRepository adminCsrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer(cookie -> cookie.secure(secureCookie).sameSite("Lax"));
		return repository;
	}

	/** CSRF를 확인할 요청: 관리자 체인 안의 상태 변경 요청(GET · HEAD · OPTIONS · TRACE 제외) */
	private static RequestMatcher stateChanging() {
		return (HttpServletRequest request) -> !SAFE_METHODS.contains(request.getMethod());
	}

	/** Origin 검증 대상: 재발급 · 로그아웃(둘 다 POST). PathPatternRequestMatcher는 디코딩된 경로 · context path 제외 기준으로 비교한다 */
	private static RequestMatcher originProtectedRequests() {
		return new OrRequestMatcher(
			PathPatternRequestMatcher.pathPattern(HttpMethod.POST, REISSUE_PATH),
			PathPatternRequestMatcher.pathPattern(HttpMethod.POST, LOGOUT_PATH));
	}

	/**
	 * 비밀번호 해시. {argon2} 접두어로 인코딩한다(OWASP 1순위 권장, BE-90 비교 선정 — docs/decisions/BE-90.md).
	 * OWASP Password Storage Cheat Sheet 권장값(m=19 MiB, t=2, p=1, 솔트 16바이트 · 해시 32바이트)을 직접 지정한다 —
	 * defaultsForSpringSecurity_v5_8()의 기본값(m=16 MiB)과는 다르다.
	 * {bcrypt} 접두어가 붙은 기존 해시도 계속 검증할 수 있도록 함께 등록한다
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		Map<String, PasswordEncoder> encoders = Map.of(
			"argon2", new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2),
			"bcrypt", new BCryptPasswordEncoder());
		return new DelegatingPasswordEncoder("argon2", encoders);
	}

	/**
	 * 관리자 비밀번호 로그인. 계정 없음 · 비활성 · 비밀번호 불일치는 모두 같은 실패로 처리한다(로그인 API).
	 * 로그인에 성공하면 {bcrypt} 해시를 {argon2}로 다시 인코딩해 저장한다(UserDetailsPasswordService, BE-90)
	 */
	@Bean
	public AuthenticationManager authenticationManager(AdminUserDetailsService userDetailsService,
		PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		provider.setUserDetailsPasswordService(userDetailsService);
		return new ProviderManager(provider);
	}

	/** 관리자 세션 저장소. 관리자 체인과 로그인 서비스(AdminAuthService)가 함께 쓴다. 사용자 체인은 쓰지 않는다 */
	@Bean
	public SecurityContextRepository adminContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}
}
