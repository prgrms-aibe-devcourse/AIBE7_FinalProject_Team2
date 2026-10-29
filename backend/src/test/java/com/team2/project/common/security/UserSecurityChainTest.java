package com.team2.project.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.common.security.token.AccessTokenVerifier;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenException;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 체인(무상태 · Bearer 토큰)과 관리자 체인의 경계 (BE-57 · WBS 7.4.1). 실제 보안 필터 체인을 거친다.
 * 토큰 검증기는 가짜(member-7 → 회원 7, expired → 만료, infra-down → 인프라 오류, 그 밖 → 잘못됨). 실제 구현은 WBS 7.4.3.
 */
@Import(UserSecurityChainTest.TestBeans.class)
class UserSecurityChainTest extends ApiIntegrationTest {

	private static final String WHOAMI = "/api/v1/test-security/whoami";
	private static final String REFERRER = "strict-origin-when-cross-origin";
	private static final String PASSWORD = "correct-horse-battery";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";
	private static final String CSRF_COOKIE = "XSRF-TOKEN";

	@TestConfiguration
	static class TestBeans {

		@Bean
		@Primary	// BE-94가 실제 구현체를 빈으로 등록한 뒤에도 이 가짜 검증기를 쓴다
		AccessTokenVerifier fakeAccessTokenVerifier() {
			return token -> switch (token) {
				case "member-7" -> MemberPrincipal.user(7);
				case "expired" -> throw TokenException.expired("만료");
				case "infra-down" -> throw new IllegalStateException("Redis 연결 실패(시험)");
				default -> throw TokenException.invalid("알 수 없는 토큰");
			};
		}

		@Bean
		WhoAmIController whoAmIController() {
			return new WhoAmIController();
		}
	}

	/** 사용자 체인에서 지금 요청이 누구로 인증됐는지 돌려주는 시험용 API */
	@RestController
	static class WhoAmIController {

		@GetMapping(WHOAMI)
		Map<String, Object> whoami() {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication != null && authentication.getPrincipal() instanceof MemberPrincipal member) {
				return Map.of("member", true, "memberId", member.memberId(),
					"authorities", authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
			}
			return Map.of("member", false,
				"authorities", authentication == null ? List.of()
					: authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
		}
	}

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

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	// ---- 사용자 체인 ----

	@Test
	@DisplayName("https 요청이어도 백엔드는 HSTS를 붙이지 않는다 — 운영 Nginx가 한 값으로 붙인다 (COMMON-67). 다른 보안 헤더는 그대로")
	void secureRequest_noHsts_otherHeadersKept() throws Exception {
		// 사용자 체인
		MvcResult user = mockMvc.perform(get(WHOAMI).secure(true))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist("Strict-Transport-Security"))
			.andExpect(header().string("Referrer-Policy", REFERRER))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andReturn();
		assertThat(user.getResponse().getStatus()).isEqualTo(200);
		// 관리자 체인 (CSRF 토큰 발급은 로그인 없이 열린 경로)
		mockMvc.perform(get(SecurityConfig.CSRF_PATH).secure(true))
			.andExpect(header().doesNotExist("Strict-Transport-Security"))
			.andExpect(header().string("Referrer-Policy", REFERRER));
	}

	@Test
	void noToken_anonymous_noSession_referrerPolicy() throws Exception {
		MvcResult result = mockMvc.perform(get(WHOAMI))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.member").value(false))
			.andExpect(header().string("Referrer-Policy", REFERRER))
			.andReturn();
		assertThat(result.getRequest().getSession(false)).isNull();
		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
	}

	@Test
	void validToken_authenticatedAsMember_noSession() throws Exception {
		MvcResult result = mockMvc.perform(get(WHOAMI).header(HttpHeaders.AUTHORIZATION, bearer("member-7")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.member").value(true))
			.andExpect(jsonPath("$.memberId").value(7))
			.andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"))
			.andReturn();
		assertThat(result.getRequest().getSession(false)).isNull();
		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
		// 인증은 그 요청에서만. 다음 요청은 다시 익명
		mockMvc.perform(get(WHOAMI)).andExpect(jsonPath("$.member").value(false));
	}

	@Test
	void lowercaseBearerScheme_accepted() throws Exception {
		mockMvc.perform(get(WHOAMI).header(HttpHeaders.AUTHORIZATION, "bearer member-7"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberId").value(7));
	}

	@Test
	void expiredToken_401TokenExpired_evenOnPermitAllPath() throws Exception {
		mockMvc.perform(get("/api/v1/cases").header(HttpHeaders.AUTHORIZATION, bearer("expired")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
			.andExpect(jsonPath("$.message").value("로그인이 만료되었습니다."));
	}

	@Test
	void badTokens_401TokenInvalid() throws Exception {
		String[] headers = {bearer("unknown"), "Bearer ", "Bearer    ", "Bearer", "BEARER ",
			"Bearer member-7, Bearer unknown", "Bearer member 7", "Bearer member-7;x"};
		for (String value : headers) {
			mockMvc.perform(get("/api/v1/cases").header(HttpHeaders.AUTHORIZATION, value))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
		}
	}

	/** Bearer가 아닌 스킴은 검증하지 않고 익명으로 통과 (앞단 Basic 인증 · 모니터링 scrape 대비, PR #106 리뷰) */
	@Test
	void nonBearerScheme_passesThroughAsAnonymous() throws Exception {
		String[] headers = {"Basic dXNlcjpwYXNz", "token-without-scheme", "Bearerx member-7", "Digest username=\"x\""};
		for (String value : headers) {
			MvcResult result = mockMvc.perform(get(WHOAMI).header(HttpHeaders.AUTHORIZATION, value))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.member").value(false))
				.andReturn();
			assertThat(result.getRequest().getSession(false)).isNull();
			assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
		}
	}

	@Test
	void duplicateAuthorizationHeaders_rejectedInEitherOrder() throws Exception {
		mockMvc.perform(get(WHOAMI).header(HttpHeaders.AUTHORIZATION, bearer("member-7"), "Basic dXNlcjpwYXNz"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
		mockMvc.perform(get(WHOAMI).header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz", bearer("member-7")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	@Test
	void infrastructureError_500InternalError_notTokenError() throws Exception {
		mockMvc.perform(get(WHOAMI).header(HttpHeaders.AUTHORIZATION, bearer("infra-down")))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
	}

	@Test
	void userPost_withToken_noCsrfNeeded() throws Exception {
		long caseId = insertCase("FRAUD", "PUBLISHED");

		mockMvc.perform(post(experienceUrl(caseId)).header(HttpHeaders.AUTHORIZATION, bearer("member-7")))
			.andExpect(status().isCreated());
	}

	// ---- 관리자 체인과의 경계 ----

	private MockHttpSession adminLogin() throws Exception {
		String email = "admin-" + UUID.randomUUID() + "@example.com";
		AdminAccount account = accountRepository.save(
			AdminAccount.withPassword(email, passwordEncoder.encode(PASSWORD), "테스트 관리자"));
		createdAdmins.add(account.getId());
		Cookie csrf = mockMvc.perform(get("/api/v1/admin/auth/csrf"))
			.andExpect(status().isOk())
			.andExpect(header().string("Referrer-Policy", REFERRER))
			.andReturn().getResponse().getCookie(CSRF_COOKIE);
		MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
				.cookie(csrf).header(CSRF_HEADER, csrf.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	@Test
	void adminSession_notUsedOnUserChain() throws Exception {
		MockHttpSession session = adminLogin();

		mockMvc.perform(get(WHOAMI).session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.member").value(false))
			.andExpect(jsonPath("$.authorities[0]").value("ROLE_ANONYMOUS"));
	}

	@Test
	void memberToken_cannotAccessAdminApi() throws Exception {
		mockMvc.perform(get("/api/v1/admin/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("member-7")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void badBearer_ignoredOnAdminChain_sessionStillWorks() throws Exception {
		MockHttpSession session = adminLogin();

		mockMvc.perform(get("/api/v1/admin/auth/me").session(session).header(HttpHeaders.AUTHORIZATION, bearer("garbage")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("테스트 관리자"));
	}
}
