package com.team2.project.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 HTTP 로그인/서블릿 세션 저장소로 잘못된 세션 ID와 유휴 만료를 확인한다(BE-88).
 * 테스트 리스너로 해당 세션의 만료 시간을 1초로 줄인다. 기본 30분 경과나 브라우저 쿠키 정책 검증은 아니다.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "management.server.port=0")
@Import(AdminAuthSessionHttpTest.SessionProbeConfiguration.class)
class AdminAuthSessionHttpTest {

	private static final String PASSWORD = "correct-horse-battery";
	private static final String SESSION = "NLNB_ADMIN_SESSION";
	private static final String CSRF = "XSRF-TOKEN";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";
	private static final String AUTH = "/api/v1/admin/auth";
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@LocalServerPort int port;
	@Autowired AdminAccountRepository accounts;
	@Autowired PasswordEncoder passwordEncoder;
	@Autowired JdbcTemplate jdbc;
	@Autowired JsonMapper jsonMapper;
	@Autowired ServletListenerRegistrationBean<SessionProbe> sessionProbeRegistration;
	private final List<Long> createdAdmins = new ArrayList<>();

	@Test
	void unknownSessionCookie_get401_postDependsOnCsrf() throws Exception {
		String unknownSession = UUID.randomUUID().toString();
		String csrf = cookie(send("GET", "/csrf", "", null, null), CSRF);
		assertError(send("GET", "/me", sessionCookie(unknownSession), null, null), 401, "UNAUTHORIZED");
		assertPostMatrix(unknownSession, csrf);
	}

	@Test
	void authenticatedSession_idleTimeout_get401_postDependsOnCsrf() throws Exception {
		String email = "be88-" + UUID.randomUUID() + "@example.com";
		AdminAccount account = accounts.save(AdminAccount.withPassword(email, passwordEncoder.encode(PASSWORD), "세션 정책 테스트"));
		createdAdmins.add(account.getId());
		String initialCsrf = cookie(send("GET", "/csrf", "", null, null), CSRF);
		HttpResponse<String> login = send("POST", "/login", csrfCookie(initialCsrf), initialCsrf,
			jsonMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD)));
		assertThat(login.statusCode()).isEqualTo(200);
		String sessionId = cookie(login, SESSION);
		String csrf = cookie(login, CSRF);
		assertThat(send("GET", "/me", sessionCookie(sessionId), null, null).statusCode()).isEqualTo(200);

		SessionProbe probe = sessionProbeRegistration.getListener();
		HttpSession session = probe.sessions.get(sessionId);
		assertThat(session).isNotNull();
		assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
		session.setMaxInactiveInterval(1);
		Thread.sleep(1500); // 추가 요청 없이 기다린 뒤 다음 요청에서 컨테이너가 유휴 만료를 판정하게 한다.

		assertError(send("GET", "/me", sessionCookie(sessionId), null, null), 401, "UNAUTHORIZED");
		assertThat(probe.destroyed).contains(sessionId);
		assertPostMatrix(sessionId, csrf);
	}

	private void assertPostMatrix(String sessionId, String csrf) throws Exception {
		// CookieCsrfTokenRepository의 토큰은 관리자 HttpSession과 별개다. 만료된 세션 ID도 계속 전송한다.
		assertError(send("POST", "/logout", sessionCookie(sessionId) + "; " + csrfCookie(csrf), csrf, null), 401, "UNAUTHORIZED");
		assertError(send("POST", "/logout", sessionCookie(sessionId), null, null), 403, "FORBIDDEN");
		assertError(send("POST", "/logout", sessionCookie(sessionId) + "; " + csrfCookie(csrf), "wrong-token", null), 403, "FORBIDDEN");
	}

	private HttpResponse<String> send(String method, String path, String cookies, String csrf, String body) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + AUTH + path))
			.timeout(Duration.ofSeconds(10));
		// HTTP 테스트는 Secure 쿠키도 명시적으로 보낸다. 브라우저의 Secure/SameSite 처리를 흉내 내지 않는다.
		if (!cookies.isEmpty()) request.header("Cookie", cookies);
		if (csrf != null) request.header(CSRF_HEADER, csrf);
		if (body != null) request.header("Content-Type", "application/json");
		return CLIENT.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
			: HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private String cookie(HttpResponse<String> response, String name) {
		return response.headers().allValues("set-cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
			.filter(cookie -> name.equals(cookie.getName())).map(HttpCookie::getValue).findFirst().orElseThrow();
	}

	private String sessionCookie(String value) {
		return SESSION + "=" + value;
	}

	private String csrfCookie(String value) {
		return CSRF + "=" + value;
	}

	private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
	}

	@AfterEach
	void cleanupOnlyOwnedSessionsAndRows() {
		SessionProbe probe = sessionProbeRegistration.getListener();
		for (HttpSession session : List.copyOf(probe.sessions.values())) {
			try {
				session.invalidate();
			} catch (IllegalStateException alreadyExpired) {
				// 컨테이너에서 이미 만료된 이 테스트의 세션이다.
			}
		}
		probe.destroyed.clear();
		createdAdmins.forEach(id -> jdbc.update("DELETE FROM admin_audit_log WHERE admin_id = ?", id));
		accounts.deleteAllById(createdAdmins);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SessionProbeConfiguration {
		@Bean
		ServletListenerRegistrationBean<SessionProbe> policySessionProbe() {
			return new ServletListenerRegistrationBean<>(new SessionProbe());
		}
	}

	static final class SessionProbe implements HttpSessionListener {
		final Map<String, HttpSession> sessions = new ConcurrentHashMap<>();
		final Set<String> destroyed = ConcurrentHashMap.newKeySet();

		@Override
		public void sessionCreated(HttpSessionEvent event) {
			sessions.put(event.getSession().getId(), event.getSession());
		}

		@Override
		public void sessionDestroyed(HttpSessionEvent event) {
			String id = event.getSession().getId();
			sessions.remove(id);
			destroyed.add(id);
		}
	}
}
