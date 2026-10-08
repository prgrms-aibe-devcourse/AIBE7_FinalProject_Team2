package com.team2.project.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 관리자 로그인 · 권한 경계 · CSRF (BE-39). 실제 보안 필터 체인을 거친다 */
class AdminAuthApiTest extends ApiIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";
	private static final String CSRF_COOKIE = "XSRF-TOKEN";

	@Autowired
	private AdminAccountRepository accountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

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
		assertThat(session.isInvalid()).isTrue();
		mockMvc.perform(get("/api/v1/admin/auth/me"))
			.andExpect(status().isUnauthorized());
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
}
