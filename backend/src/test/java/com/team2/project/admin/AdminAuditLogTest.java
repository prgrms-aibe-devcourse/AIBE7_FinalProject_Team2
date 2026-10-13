package com.team2.project.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.admin.audit.AdminAuditAction;
import com.team2.project.admin.audit.AdminAuditLogger;
import com.team2.project.admin.audit.AuditTargetType;
import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 감사 로그 (BE-68): 로그인 성공 · 실패 · 로그아웃 기록, 입력값 · 비밀번호 비저장,
 * 저장 실패 처리(record는 함께 롤백 · recordQuietly는 독립), 로그인 성공 기록 실패 시 세션 없음
 */
class AdminAuditLogTest extends ApiIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";
	private static final String CSRF_COOKIE = "XSRF-TOKEN";

	@Autowired
	private AdminAccountRepository accountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoSpyBean
	private AdminAuditLogger auditLogger;

	private final List<Long> createdAdmins = new ArrayList<>();
	private final List<String> usedEmails = new ArrayList<>();
	private Timestamp startedAt;

	@BeforeEach
	void markStart() {
		startedAt = jdbcTemplate.queryForObject("SELECT clock_timestamp() - interval '1 second'", Timestamp.class);
	}

	@AfterEach
	void cleanUp() {
		usedEmails.forEach(email -> jdbcTemplate.update("DELETE FROM admin_audit_log WHERE actor_email = ?", email));
		// 누구인지 없는 기록(모르는 입력 · 이메일 칸 비밀번호)도 이 테스트가 만든 것은 지운다 (#118 리뷰)
		jdbcTemplate.update("DELETE FROM admin_audit_log WHERE actor_email IS NULL AND created_at >= ?", startedAt);
		usedEmails.clear();
		accountRepository.deleteAllById(createdAdmins);
		createdAdmins.clear();
	}

	private AdminAccount createAdmin() {
		String email = "audit-" + UUID.randomUUID() + "@example.com";
		AdminAccount account = accountRepository.save(
			AdminAccount.withPassword(email, passwordEncoder.encode(PASSWORD), "감사 테스트"));
		createdAdmins.add(account.getId());
		usedEmails.add(email);
		return account;
	}

	private Cookie csrfCookie() throws Exception {
		return mockMvc.perform(get("/api/v1/admin/auth/csrf")).andReturn().getResponse().getCookie(CSRF_COOKIE);
	}

	private MvcResult login(String email, String password, int expectedStatus) throws Exception {
		Cookie csrf = csrfCookie();
		return mockMvc.perform(post("/api/v1/admin/auth/login").cookie(csrf).header(CSRF_HEADER, csrf.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
			.andExpect(status().is(expectedStatus))
			.andReturn();
	}

	private List<Map<String, Object>> logsOf(String email) {
		return jdbcTemplate.queryForList(
			"SELECT admin_id, actor_email, action, target_type, target_id, detail::text AS detail, ip_address "
				+ "FROM admin_audit_log WHERE actor_email = ? ORDER BY id", email);
	}

	/** 감사 로그 어디에든(이메일 · 상세) 이 값이 들어 있는 행 수 */
	private long rowsContaining(String value) {
		return jdbcTemplate.queryForObject(
			"SELECT count(*) FROM admin_audit_log WHERE actor_email ILIKE ? OR detail::text ILIKE ?", Long.class,
			"%" + value + "%", "%" + value + "%");
	}

	private long unknownFailures() {
		return jdbcTemplate.queryForObject(
			"SELECT count(*) FROM admin_audit_log WHERE action = 'LOGIN_FAILURE' AND admin_id IS NULL AND actor_email IS NULL",
			Long.class);
	}

	@Test
	void loginSuccess_recordedWithAdminIdAndIp() throws Exception {
		AdminAccount admin = createAdmin();

		login(admin.getEmail().toUpperCase(), PASSWORD, 200);

		assertThat(logsOf(admin.getEmail())).singleElement().satisfies(row -> {
			assertThat(row.get("action")).isEqualTo("LOGIN_SUCCESS");
			assertThat(((Number) row.get("admin_id")).longValue()).isEqualTo(admin.getId());
			assertThat(row.get("ip_address")).isNotNull();
			assertThat(row.get("target_type")).isNull();
		});
	}

	/** 계정이 있는 이메일의 실패는 그 계정으로, 모르는 입력은 누구인지 없이 남긴다. 비밀번호는 어디에도 없다 */
	@Test
	void loginFailure_knownAccountRecorded_unknownInputNotStored() throws Exception {
		AdminAccount admin = createAdmin();
		String unknown = "nobody-" + UUID.randomUUID() + "@example.com";
		long unknownBefore = unknownFailures();

		login(admin.getEmail(), "wrong-password-xyz", 401);
		login(unknown, PASSWORD, 401);

		assertThat(logsOf(admin.getEmail())).singleElement().satisfies(row -> {
			assertThat(row.get("action")).isEqualTo("LOGIN_FAILURE");
			assertThat(((Number) row.get("admin_id")).longValue()).isEqualTo(admin.getId());
			assertThat(row.get("detail")).isNull();
		});
		assertThat(rowsContaining(unknown)).isZero();
		assertThat(unknownFailures()).isEqualTo(unknownBefore + 1);
		assertThat(rowsContaining("wrong-password-xyz")).isZero();
	}

	/** 이메일 칸에 비밀번호를 잘못 넣어도 그 값은 감사 로그에 남지 않는다 (Codex 리뷰 1) */
	@Test
	void loginFailure_passwordTypedIntoEmailField_notStored() throws Exception {
		String secret = "S3cret-" + UUID.randomUUID();

		login(secret, PASSWORD, 401);

		assertThat(rowsContaining(secret)).isZero();
	}

	@Test
	void logout_recorded() throws Exception {
		AdminAccount admin = createAdmin();
		MvcResult result = login(admin.getEmail(), PASSWORD, 200);
		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
		Cookie csrf = result.getResponse().getCookie(CSRF_COOKIE);

		mockMvc.perform(post("/api/v1/admin/auth/logout").session(session).cookie(csrf).header(CSRF_HEADER, csrf.getValue()))
			.andExpect(status().isNoContent());

		assertThat(logsOf(admin.getEmail())).extracting(row -> row.get("action"))
			.containsExactly("LOGIN_SUCCESS", "LOGOUT");
	}

	/** 로그인 성공 기록을 저장하지 못하면 로그인도 실패하고 세션을 만들지 않는다 (Codex 리뷰 5) */
	@Test
	void loginSuccess_auditFailure_noSession() throws Exception {
		AdminAccount admin = createAdmin();
		doThrow(new IllegalStateException("감사 로그 저장 실패(시험)")).when(auditLogger).record(
			argThat(principal -> principal != null && admin.getEmail().equals(principal.email())),
			eq(AdminAuditAction.LOGIN_SUCCESS), isNull(), isNull(), isNull(), any());

		MvcResult result = login(admin.getEmail(), PASSWORD, 500);

		assertThat(result.getRequest().getSession(false)).isNull();
		assertThat(logsOf(admin.getEmail())).isEmpty();
	}

	/** record는 부르는 쪽 트랜잭션에 들어간다: 바깥 일이 롤백되면 기록도 없어진다 (Codex 리뷰 5) */
	@Test
	void record_joinsOuterTransaction_rolledBackTogether() {
		AdminAccount admin = createAdmin();
		AdminPrincipal principal = AdminPrincipal.from(admin);

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			auditLogger.record(principal, AdminAuditAction.CASE_PUBLISH, AuditTargetType.CASE, 999_001L,
				Map.of("note", "시험"), null);
			status.setRollbackOnly();
		});

		assertThat(logsOf(admin.getEmail())).isEmpty();
	}

	/** recordQuietly는 별도 트랜잭션이다: 바깥 일이 롤백돼도 기록은 남는다 (Codex 리뷰 5) */
	@Test
	void recordQuietly_independentOfOuterRollback() {
		AdminAccount admin = createAdmin();
		AdminPrincipal principal = AdminPrincipal.from(admin);

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			auditLogger.recordQuietly(principal, AdminAuditAction.LOGOUT, null);
			status.setRollbackOnly();
		});

		assertThat(logsOf(admin.getEmail())).extracting(row -> row.get("action")).containsExactly("LOGOUT");
	}

	/** 읽기 행동(A-7)은 readOnly 트랜잭션 안에서 불러도 따로 저장되고, 바깥 롤백과 관계없이 남는다 (#118 리뷰) */
	@Test
	void recordRead_insideReadOnlyTransaction_savedSeparately() {
		AdminAccount admin = createAdmin();
		AdminPrincipal principal = AdminPrincipal.from(admin);
		TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
		readOnly.setReadOnly(true);

		readOnly.executeWithoutResult(status -> {
			auditLogger.recordRead(principal, AdminAuditAction.SOURCE_VIEW, AuditTargetType.CASE_SOURCE, 999_003L, null);
			status.setRollbackOnly();
		});

		assertThat(logsOf(admin.getEmail())).singleElement().satisfies(row -> {
			assertThat(row.get("action")).isEqualTo("SOURCE_VIEW");
			assertThat(row.get("target_type")).isEqualTo("CASE_SOURCE");
			assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(999_003L);
		});
	}

	/** 읽기 행동 기록을 저장하지 못하면 예외를 던진다(기록 없는 열람을 막는다) */
	@Test
	void recordRead_storageFailure_throws() {
		AdminPrincipal principal = new AdminPrincipal(999_004L, "y".repeat(300) + "@example.com", "없는 관리자", null, true);

		assertThatThrownBy(() -> auditLogger.recordRead(principal, AdminAuditAction.SOURCE_VIEW,
			AuditTargetType.CASE_SOURCE, 1L, null)).isInstanceOf(RuntimeException.class);
	}

	/** detail의 null 값(A-11 "null = 지움")도 그대로 저장된다 — Map.copyOf였다면 NPE (#118 리뷰) */
	@Test
	void record_detailWithNullValue_saved() {
		AdminAccount admin = createAdmin();
		Map<String, Object> detail = new HashMap<>();
		detail.put("changedFields", List.of("keywords"));
		detail.put("keywords", null);

		auditLogger.record(AdminPrincipal.from(admin), AdminAuditAction.CASE_UPDATE, AuditTargetType.CASE, 999_005L,
			detail, null);

		assertThat(logsOf(admin.getEmail())).singleElement()
			.satisfies(row -> assertThat((String) row.get("detail")).contains("\"keywords\": null"));
	}

	/** 조용한 기록은 저장이 실패해도 예외를 던지지 않는다(로그인 실패 · 로그아웃 응답이 500으로 바뀌지 않게) */
	@Test
	void recordQuietly_storageFailure_doesNotThrow() {
		String tooLong = "x".repeat(300) + "@example.com";	// actor_email varchar(254) 초과 → 저장 실패
		AdminPrincipal principal = new AdminPrincipal(999_002L, tooLong, "없는 관리자", null, true);

		auditLogger.recordQuietly(principal, AdminAuditAction.LOGOUT, null);

		assertThat(rowsContaining("x".repeat(300))).isZero();
	}

	/** DB 제약: 모르는 행동 · 대상 종류 없이 대상 ID만 있는 행은 거절한다 */
	@Test
	void constraints_rejectUnknownActionAndHalfTarget() {
		assertThatThrownBy(() -> jdbcTemplate.update(
			"INSERT INTO admin_audit_log (action) VALUES ('DELETE_EVERYTHING')"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("chk_admin_audit_log_action");
		assertThatThrownBy(() -> jdbcTemplate.update(
			"INSERT INTO admin_audit_log (action, target_id) VALUES ('CASE_PUBLISH', 1)"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("chk_admin_audit_log_target");
	}

	/** 계정을 지워도 기록은 남고 admin_id만 비워진다(누구였는지는 actor_email로 남는다) */
	@Test
	void accountDeleted_logKeptWithEmail() throws Exception {
		AdminAccount admin = createAdmin();
		login(admin.getEmail(), PASSWORD, 200);

		jdbcTemplate.update("DELETE FROM admin_account WHERE id = ?", admin.getId());

		assertThat(logsOf(admin.getEmail())).singleElement().satisfies(row -> {
			assertThat(row.get("admin_id")).isNull();
			assertThat(row.get("action")).isEqualTo("LOGIN_SUCCESS");
		});
	}
}
