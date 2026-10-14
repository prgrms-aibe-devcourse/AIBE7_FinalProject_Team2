package com.team2.project.admin.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 판결 후보 승인 · 반려 A-9 · A-10 (API 명세 8-2, BE-42). 실제 로그인 세션 + CSRF 토큰으로 부른다.
 */
class AdminReviewDecisionApiTest extends ApiIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";
	private static final String CSRF_COOKIE = "XSRF-TOKEN";

	@Autowired AdminAccountRepository accountRepository;
	@Autowired PasswordEncoder passwordEncoder;

	private AdminAccount admin;
	private MockHttpSession session;
	private Cookie csrf;
	private long caseId;

	@BeforeEach
	void setUp() throws Exception {
		admin = accountRepository.save(AdminAccount.withPassword("decision-" + UUID.randomUUID() + "@example.com",
			passwordEncoder.encode(PASSWORD), "승인 테스트"));
		login();
		caseId = insertCase("MURDER", "DRAFT");
	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM ai_generation WHERE judgment_id IN (SELECT id FROM judgment WHERE case_id = ?)",
			caseId);
		jdbcTemplate.update("DELETE FROM admin_audit_log WHERE actor_email = ?", admin.getEmail());
		accountRepository.deleteById(admin.getId());
	}

	@Test
	@DisplayName("A-9: 승인하면 공개 판결이 되고, 같은 주체의 기존 공개 판결은 비공개(승인 상태는 유지) · 사건 DRAFT → REVIEW · AI 기록 · 감사 로그")
	void approve_replacesPublishedAndRecords() throws Exception {
		long oldPublished = candidate("AI", true);	// 시드처럼 검수 기록 없이 공개된 판결
		long candidate = candidate("AI", false);
		generation(candidate);

		perform(post(judgmentUrl(candidate) + "/approve"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.judgmentId").value(candidate))
			.andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
			.andExpect(jsonPath("$.isPublished").value(true))
			.andExpect(jsonPath("$.reviewedBy").value(admin.getEmail()))
			.andExpect(jsonPath("$.reviewedAt").exists());

		assertThat(publishedAiIds()).containsExactly(candidate);
		assertThat(reviewStatusOf(oldPublished)).isEqualTo("APPROVED");	// 내려도 "대기"로 보이지 않는다
		assertThat(caseStatus()).isEqualTo("REVIEW");
		assertThat(jdbcTemplate.queryForObject("SELECT review_status FROM ai_generation WHERE judgment_id = ?",
			String.class, candidate)).isEqualTo("APPROVED");
		assertThat(auditActions()).containsExactly("JUDGMENT_APPROVE");
	}

	@Test
	@DisplayName("A-9: 이미 승인돼 공개 중인 후보를 다시 승인하면 아무것도 바꾸지 않고 200 (감사 로그도 없음)")
	void approve_twice_isNoOp() throws Exception {
		long candidate = candidate("COURT", false);
		perform(post(judgmentUrl(candidate) + "/approve")).andExpect(status().isOk());
		perform(post(judgmentUrl(candidate) + "/approve"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewStatus").value("APPROVED"));
		assertThat(auditActions()).containsExactly("JUDGMENT_APPROVE");
	}

	@Test
	@DisplayName("A-9: 공개한 사건은 판결 교체 금지 409 CASE_PUBLISHED, 반려한 후보는 409 CANDIDATE_REJECTED")
	void approve_rejectedStates() throws Exception {
		long rejected = candidate("AI", false);
		jdbcTemplate.update("UPDATE judgment SET review_status = 'REJECTED', review_note = '사유' WHERE id = ?", rejected);
		perform(post(judgmentUrl(rejected) + "/approve"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.currentStatus").value("DRAFT"))
			.andExpect(jsonPath("$.details[0].reason").value("CANDIDATE_REJECTED"));

		long candidate = candidate("AI", false);
		jdbcTemplate.update("UPDATE legal_case SET status = 'PUBLISHED', published_at = now() WHERE id = ?", caseId);
		perform(post(judgmentUrl(candidate) + "/approve"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.currentStatus").value("PUBLISHED"))
			.andExpect(jsonPath("$.details[0].field").value("case"))
			.andExpect(jsonPath("$.details[0].reason").value("CASE_PUBLISHED"));
		assertThat(publishedAiIds()).isEmpty();
		assertThat(auditActions()).isEmpty();
	}

	@Test
	@DisplayName("A-9: 같은 사건의 두 AI 후보를 동시에 승인해도 공개 판결은 하나 (사건 행 잠금으로 차례 처리)")
	void approve_concurrent_onePublished() throws Exception {
		List<Long> candidates = List.of(candidate("AI", false), candidate("AI", false));
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		List<Future<Integer>> futures = new ArrayList<>();
		try {
			for (Long id : candidates) {
				Callable<Integer> call = () -> {
					ready.countDown();
					ready.await();
					return perform(post(judgmentUrl(id) + "/approve")).andReturn().getResponse().getStatus();
				};
				futures.add(pool.submit(call));
			}
			for (Future<Integer> future : futures) {
				assertThat(future.get()).isEqualTo(200);
			}
		} finally {
			pool.shutdownNow();
		}
		assertThat(publishedAiIds()).hasSize(1).isSubsetOf(candidates);
	}

	@Test
	@DisplayName("A-10: 반려하면 사유 · 검수자가 남고 비공개 그대로, AI 기록 · 사건 REVIEW · 감사 로그")
	void reject_recordsReason() throws Exception {
		long candidate = candidate("AI", false);
		generation(candidate);
		perform(post(judgmentUrl(candidate) + "/reject").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"참고 판례에 다른 죄명 판례가 섞였음\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewStatus").value("REJECTED"))
			.andExpect(jsonPath("$.isPublished").value(false))
			.andExpect(jsonPath("$.reviewNote").value("참고 판례에 다른 죄명 판례가 섞였음"))
			.andExpect(jsonPath("$.reviewedBy").value(admin.getEmail()));
		assertThat(caseStatus()).isEqualTo("REVIEW");
		assertThat(jdbcTemplate.queryForObject("SELECT review_status FROM ai_generation WHERE judgment_id = ?",
			String.class, candidate)).isEqualTo("REJECTED");
		assertThat(auditActions()).containsExactly("JUDGMENT_REJECT");
	}

	@Test
	@DisplayName("A-10: 공개한 사건에서도 공개 중이 아닌 후보는 반려할 수 있다")
	void reject_onPublishedCase_allowed() throws Exception {
		long candidate = candidate("COURT", false);
		jdbcTemplate.update("UPDATE legal_case SET status = 'PUBLISHED', published_at = now() WHERE id = ?", caseId);
		perform(post(judgmentUrl(candidate) + "/reject").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"다른 사건 판결문\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewStatus").value("REJECTED"));
		assertThat(caseStatus()).isEqualTo("PUBLISHED");
	}

	@Test
	@DisplayName("A-10: 교체로 내려간 승인 후보도 반려할 수 있다(감사 로그에 승인 이력), 사유는 앞뒤 공백을 자른 값으로 검증 · 저장")
	void reject_replacedApproved_andStripsReason() throws Exception {
		long first = candidate("AI", false);
		long second = candidate("AI", false);
		perform(post(judgmentUrl(first) + "/approve")).andExpect(status().isOk());
		perform(post(judgmentUrl(second) + "/approve")).andExpect(status().isOk());
		assertThat(reviewStatusOf(first)).isEqualTo("APPROVED");

		perform(post(judgmentUrl(first) + "/reject").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"  다시 보니 양형 근거 부족  \"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewStatus").value("REJECTED"))
			.andExpect(jsonPath("$.isPublished").value(false))
			.andExpect(jsonPath("$.reviewNote").value("다시 보니 양형 근거 부족"));
		assertThat(publishedAiIds()).containsExactly(second);
		assertThat(auditActions()).containsExactly("JUDGMENT_APPROVE", "JUDGMENT_APPROVE", "JUDGMENT_REJECT");

		long padded = candidate("COURT", false);	// 앞뒤 공백 포함 504자 → 자르면 500자라 통과
		perform(post(judgmentUrl(padded) + "/reject").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"  " + "가".repeat(500) + "  \"}"))
			.andExpect(status().isOk());
		assertThat(jdbcTemplate.queryForObject("SELECT length(review_note) FROM judgment WHERE id = ?", Integer.class, padded))
			.isEqualTo(500);
	}

	@Test
	@DisplayName("A-10: 공개 중 후보는 409 CANDIDATE_PUBLISHED, 이미 반려한 후보는 409 CANDIDATE_REJECTED, 사유 빈 값 · 500자 초과는 400")
	void reject_rejectedStates() throws Exception {
		long published = candidate("AI", true);
		perform(post(judgmentUrl(published) + "/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"사유\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.details[0].reason").value("CANDIDATE_PUBLISHED"));

		long candidate = candidate("AI", false);
		for (String body : List.of("{}", "{\"reason\":\"  \"}", "{\"reason\":\"" + "가".repeat(501) + "\"}")) {
			perform(post(judgmentUrl(candidate) + "/reject").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		perform(post(judgmentUrl(candidate) + "/reject").contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"" + "가".repeat(500) + "\"}")).andExpect(status().isOk());
		perform(post(judgmentUrl(candidate) + "/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"다시\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.details[0].reason").value("CANDIDATE_REJECTED"));
		assertThat(auditActions()).containsExactly("JUDGMENT_REJECT");
	}

	@Test
	@DisplayName("CSRF 토큰이 없으면 403, 없는 판결 · 사용자 판결은 404 JUDGMENT_NOT_FOUND")
	void csrfAndNotFound() throws Exception {
		long candidate = candidate("AI", false);
		mockMvc.perform(post(judgmentUrl(candidate) + "/approve").session(session)).andExpect(status().isForbidden());

		UUID user = UUID.randomUUID();
		insertExperience(user, caseId, 1, "VERDICT_CONFIRMED");
		Long userJudgment = jdbcTemplate.queryForObject("""
			INSERT INTO judgment (case_id, subject_type, timing, experience_id, penalty_type, prison_months, is_published)
			SELECT ?, 'USER', 'FINAL', id, 'PRISON', 60, true FROM experience WHERE anonymous_user_id = ?
			RETURNING id""", Long.class, caseId, user);
		for (long id : List.of(-1L, userJudgment)) {
			perform(post(judgmentUrl(id) + "/approve"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JUDGMENT_NOT_FOUND"));
		}
		assertThat(publishedAiIds()).isEmpty();
	}

	// --- 도움 함수 ---

	private org.springframework.test.web.servlet.ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.session(session).cookie(csrf).header(CSRF_HEADER, csrf.getValue()));
	}

	private static String judgmentUrl(long id) {
		return "/api/v1/admin/judgments/" + id;
	}

	private long candidate(String subjectType, boolean published) {
		return jdbcTemplate.queryForObject("""
			INSERT INTO judgment (case_id, subject_type, timing, penalty_type, prison_months, is_published)
			VALUES (?, ?, 'FINAL', 'PRISON', 144, ?) RETURNING id""", Long.class, caseId, subjectType, published);
	}

	private void generation(long judgmentId) {
		jdbcTemplate.update("""
			INSERT INTO ai_generation (judgment_id, model_name, prompt_version, review_status)
			VALUES (?, '시험 모델', 'v-test', 'PENDING')""", judgmentId);
	}

	private List<Long> publishedAiIds() {
		return jdbcTemplate.queryForList(
			"SELECT id FROM judgment WHERE case_id = ? AND subject_type = 'AI' AND is_published", Long.class, caseId);
	}

	private String reviewStatusOf(long judgmentId) {
		return jdbcTemplate.queryForObject("SELECT review_status FROM judgment WHERE id = ?", String.class, judgmentId);
	}

	private String caseStatus() {
		return jdbcTemplate.queryForObject("SELECT status FROM legal_case WHERE id = ?", String.class, caseId);
	}

	private List<String> auditActions() {
		return jdbcTemplate.queryForList("""
			SELECT action FROM admin_audit_log WHERE actor_email = ? AND action LIKE 'JUDGMENT_%' ORDER BY id""",
			String.class, admin.getEmail());
	}

	private void login() throws Exception {
		Cookie first = mockMvc.perform(get("/api/v1/admin/auth/csrf")).andReturn().getResponse().getCookie(CSRF_COOKIE);
		MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login").cookie(first)
				.header(CSRF_HEADER, first.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(admin.getEmail(), PASSWORD)))
			.andExpect(status().isOk())
			.andReturn();
		session = (MockHttpSession) result.getRequest().getSession(false);
		csrf = result.getResponse().getCookie(CSRF_COOKIE);	// 로그인하면 CSRF 토큰이 바뀐다 (BE-67)
	}
}
