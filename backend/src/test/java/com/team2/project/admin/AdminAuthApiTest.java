package com.team2.project.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.admin.service.AdminUserDetailsService;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 관리자 로그인 · 권한 경계 · CSRF (BE-39, 보강 BE-67). 실제 보안 필터 체인을 거친다 */
class AdminAuthApiTest extends ApiIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";
	private static final String CSRF_COOKIE = "XSRF-TOKEN";

	@Autowired
	private AdminAccountRepository accountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AdminUserDetailsService userDetailsService;

	private final List<Long> createdAdmins = new ArrayList<>();

	@AfterEach
	void deleteAdmins() {
		accountRepository.deleteAllById(createdAdmins);
		createdAdmins.clear();
	}

	private String createAdmin(boolean enabled) {
		String email = "admin-" + UUID.randomUUID() + "@example.com";
		AdminAccount account = accountRepository.save(
			AdminAccount.withPassword(email, passwordEncoder.encode(PASSWORD), "테스트 관리자"));
		createdAdmins.add(account.getId());
		if (!enabled) {
			jdbcTemplate.update("UPDATE admin_account SET enabled = false WHERE id = ?", account.getId());
		}
		return email;
	}

	/** 실제 화면과 같은 순서: CSRF 토큰을 받아 쿠키 · 헤더에 함께 담는다 */
	private Cookie csrfCookie() throws Exception {
		return mockMvc.perform(get("/api/v1/admin/auth/csrf"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.headerName").value(CSRF_HEADER))
			.andExpect(cookie().exists(CSRF_COOKIE))
			.andReturn().getResponse().getCookie(CSRF_COOKIE);
	}

	private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder, Cookie csrf) {
		return builder.cookie(csrf).header(CSRF_HEADER, csrf.getValue());
	}

	private MockHttpServletRequestBuilder loginRequest(String email, String password, Cookie csrf) {
		return withCsrf(post("/api/v1/admin/auth/login"), csrf)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
	}

	private MockHttpSession login(String email) throws Exception {
		MvcResult result = mockMvc.perform(loginRequest(email, PASSWORD, csrfCookie()))
			.andExpect(status().isOk())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	@Test
	void login_success_sessionGrantsAdminApi() throws Exception {
		String email = createAdmin(true);
		Instant before = Instant.now();

		MvcResult result = mockMvc.perform(loginRequest(email.toUpperCase(), PASSWORD, csrfCookie()))	// 이메일 대소문자 무시
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.displayName").value("테스트 관리자"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andReturn();
		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

		mockMvc.perform(get("/api/v1/admin/auth/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email));
		Instant lastLogin = accountRepository.findByEmail(email).orElseThrow().getLastLoginAt();
		assertThat(lastLogin).isAfterOrEqualTo(before.minusSeconds(1));
	}

	@Test
	void login_wrongPasswordUnknownEmailDisabled_sameFailure() throws Exception {
		String enabled = createAdmin(true);
		String disabled = createAdmin(false);
		String[][] attempts = {
			{enabled, "wrong-password"},
			{"nobody-" + UUID.randomUUID() + "@example.com", PASSWORD},
			{disabled, PASSWORD},
		};
		for (String[] attempt : attempts) {
			mockMvc.perform(loginRequest(attempt[0], attempt[1], csrfCookie()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
		}
	}

	@Test
	void login_withoutCsrf_forbidden() throws Exception {
		String email = createAdmin(true);

		mockMvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void login_blankFields_validationError() throws Exception {
		mockMvc.perform(loginRequest("", "", csrfCookie()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void adminApi_withoutLogin_unauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/admin/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		// 아직 없는 관리자 경로도 404가 아니라 401 (경로 존재 여부를 드러내지 않는다)
		mockMvc.perform(get("/api/v1/admin/reviews"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void logout_requiresCsrf_thenEndsSession() throws Exception {
		MockHttpSession session = login(createAdmin(true));

		mockMvc.perform(post("/api/v1/admin/auth/logout").session(session))
			.andExpect(status().isForbidden());
		mockMvc.perform(withCsrf(post("/api/v1/admin/auth/logout"), csrfCookie()).session(session))
			.andExpect(status().isNoContent());
		// 로그아웃은 세션을 무효화한다. 무효화된 세션은 다시 보낼 수 없으므로 여기서 직접 확인한다 (#104 리뷰)
		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void principal_toStringHidesPasswordHash() {
		AdminPrincipal principal = new AdminPrincipal(1L, "admin@example.com", "관리자", "{bcrypt}$2a$10$secret-hash", true);

		assertThat(principal.toString()).doesNotContain("secret-hash").contains("passwordHash=***");
		assertThat(principal.withoutPassword().toString()).contains("passwordHash=null");
	}

	@Test
	void login_existingSession_idChanges() throws Exception {
		String email = createAdmin(true);
		MockHttpSession before = new MockHttpSession();
		String beforeId = before.getId();

		MvcResult result = mockMvc.perform(loginRequest(email, PASSWORD, csrfCookie()).session(before))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(beforeId);
	}

	@Test
	void userApi_unaffected_noCsrfNoSession() throws Exception {
		long caseId = insertCase("FRAUD", "PUBLISHED");

		// 사용자 API의 POST는 CSRF 토큰 없이 그대로 동작하고, 관리자 세션 · CSRF 쿠키를 만들지 않는다
		MvcResult result = mockMvc.perform(post(experienceUrl(caseId)))
			.andExpect(status().isCreated())
			.andExpect(cookie().doesNotExist(CSRF_COOKIE))
			.andReturn();
		assertThat(result.getRequest().getSession(false)).isNull();
		assertThat(result.getResponse().getHeaders("Set-Cookie")).singleElement()
			.satisfies(value -> assertThat(value).startsWith(COOKIE + "="));
		mockMvc.perform(get("/api/v1/cases")).andExpect(status().isOk());
	}

	// ---- BE-67 로그인 보강 ----

	/**
	 * 로그인하면 CSRF 토큰이 바뀐다. 쿠키 방식(double submit)이라 서버는 "쿠키 값 = 헤더 값"만 확인하므로,
	 * 바뀐 뒤 브라우저 쿠키는 새 값이 되어 로그인 전 토큰을 헤더에 담으면 막히고, 새 토큰으로는 된다
	 */
	@Test
	void login_rotatesCsrfToken() throws Exception {
		String email = createAdmin(true);
		Cookie before = csrfCookie();

		MvcResult result = mockMvc.perform(loginRequest(email, PASSWORD, before))
			.andExpect(status().isOk())
			.andExpect(cookie().exists(CSRF_COOKIE))
			.andReturn();
		Cookie after = result.getResponse().getCookie(CSRF_COOKIE);
		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(after.getValue()).isNotBlank().isNotEqualTo(before.getValue());

		// 브라우저에는 새 쿠키가 있고, 헤더에만 로그인 전 토큰을 담은 경우
		mockMvc.perform(post("/api/v1/admin/auth/logout").session(session)
				.cookie(after).header(CSRF_HEADER, before.getValue()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(withCsrf(post("/api/v1/admin/auth/logout"), after).session(session))
			.andExpect(status().isNoContent());
	}

	/** 로그인한 뒤 계정이 비활성이 되면 다음 요청부터 401이고 세션도 끝난다 */
	@Test
	void session_accountDisabledAfterLogin_unauthorized() throws Exception {
		String email = createAdmin(true);
		MockHttpSession session = login(email);
		mockMvc.perform(get("/api/v1/admin/auth/me").session(session)).andExpect(status().isOk());

		jdbcTemplate.update("UPDATE admin_account SET enabled = false WHERE email = ?", email);

		mockMvc.perform(get("/api/v1/admin/auth/me").session(session))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		assertThat(session.isInvalid()).isTrue();
	}

	/** 비활성 계정이 CSRF 토큰 없이 상태 변경 요청을 보내도 403이 아니라 401로 끝나고 세션이 남지 않는다 (#116 리뷰) */
	@Test
	void session_accountDisabled_postWithoutCsrf_unauthorizedNotForbidden() throws Exception {
		String email = createAdmin(true);
		MockHttpSession session = login(email);

		jdbcTemplate.update("UPDATE admin_account SET enabled = false WHERE email = ?", email);

		mockMvc.perform(post("/api/v1/admin/auth/logout").session(session))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		assertThat(session.isInvalid()).isTrue();
	}

	/** 로그아웃하면 CSRF 쿠키도 지운다 (#116 리뷰) */
	@Test
	void logout_clearsCsrfCookie() throws Exception {
		MvcResult login = mockMvc.perform(loginRequest(createAdmin(true), PASSWORD, csrfCookie()))
			.andExpect(status().isOk())
			.andReturn();
		Cookie csrf = login.getResponse().getCookie(CSRF_COOKIE);
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

		mockMvc.perform(withCsrf(post("/api/v1/admin/auth/logout"), csrf).session(session))
			.andExpect(status().isNoContent())
			.andExpect(cookie().maxAge(CSRF_COOKIE, 0));
	}

	/** 로그인한 뒤 계정이 지워져도 다음 요청부터 401 */
	@Test
	void session_accountDeletedAfterLogin_unauthorized() throws Exception {
		String email = createAdmin(true);
		MockHttpSession session = login(email);

		jdbcTemplate.update("DELETE FROM admin_account WHERE email = ?", email);

		mockMvc.perform(get("/api/v1/admin/auth/me").session(session))
			.andExpect(status().isUnauthorized());
		assertThat(session.isInvalid()).isTrue();
	}

	/**
	 * 비활성 계정은 조회 단계에서 "계정 없음"과 같게 처리한다. 그래야 DaoAuthenticationProvider가 계정 없음과 같은
	 * 경로(가짜 해시로 비밀번호 확인)를 타서 응답 시간 차이로 가입된 이메일을 알아낼 수 없다
	 */
	@Test
	void userDetails_disabledAccount_treatedAsNotFound() {
		String disabled = createAdmin(false);

		assertThatThrownBy(() -> userDetailsService.loadUserByUsername(disabled))
			.isInstanceOf(UsernameNotFoundException.class);
		assertThat(userDetailsService.loadUserByUsername(createAdmin(true)).isEnabled()).isTrue();
	}
}
