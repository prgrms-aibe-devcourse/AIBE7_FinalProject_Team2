package com.team2.project.admin.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 관리자 검수 조회 API A-5 ~ A-8 (API 명세 8-2, BE-41). 실제 로그인 세션으로 부른다.
 * 시드 사건이 있을 수 있으므로 이 테스트가 만든 사건만 검증한다.
 */
class AdminReviewQueryApiTest extends ApiIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";
	private static final String CSRF_COOKIE = "XSRF-TOKEN";

	@Autowired AdminAccountRepository accountRepository;
	@Autowired PasswordEncoder passwordEncoder;

	private AdminAccount admin;
	private MockHttpSession session;
	private long caseId;
	private long overviewFactorId;
	private long aiCandidateId;
	private long courtJudgmentId;
	private long sourceId;

	@BeforeEach
	void setUp() throws Exception {
		admin = accountRepository.save(AdminAccount.withPassword("review-" + UUID.randomUUID() + "@example.com",
			passwordEncoder.encode(PASSWORD), "검수 테스트"));
		session = login(admin.getEmail());

		caseId = insertCase("MURDER", "DRAFT");
		overviewFactorId = insertFactor(caseId, "OVERVIEW", "다툼 끝에 일어났다", "다투다 일어났다", 1);
		insertFactor(caseId, "DETAIL", "범행을 인정하고 반성한다", null, 2);
		aiCandidateId = judgment("AI", false, 180);
		jdbcTemplate.update("""
			INSERT INTO ai_generation (judgment_id, model_name, prompt_version, review_status, input_snapshot)
			VALUES (?, '시험 모델', 'v-test', 'PENDING', '{"precedents": []}'::jsonb)""", aiCandidateId);
		jdbcTemplate.update("INSERT INTO judgment_factor (judgment_id, factor_id, direction) VALUES (?, ?, 'UP')",
			aiCandidateId, overviewFactorId);
		courtJudgmentId = judgment("COURT", true, 144);
		sourceId = jdbcTemplate.queryForObject("""
			INSERT INTO case_source (case_id, court_level, case_number, court_name, is_final, source_org, original_text)
			VALUES (?, 'FIRST', '2099고합000', '가상지방법원', true, '법제처', '가상 판결문 원문') RETURNING id""",
			Long.class, caseId);
	}

	@AfterEach
	void cleanUp() {
		// 부모 정리(판단 · 요소 · 원본 · 사건)보다 먼저: AI 생성 기록 · 감사 로그 · 관리자
		jdbcTemplate.update("DELETE FROM ai_generation WHERE judgment_id IN (SELECT id FROM judgment WHERE case_id = ?)",
			caseId);
		jdbcTemplate.update("DELETE FROM admin_audit_log WHERE actor_email = ?", admin.getEmail());
		accountRepository.deleteById(admin.getId());
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void withoutLogin_unauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/admin/cases")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/admin/cases/" + caseId)).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("A-5: 기본은 DRAFT · REVIEW, 대기 후보 수 · 공개 판결 여부 · 축 미확정 수를 준다")
	void listCases_defaultStatuses() throws Exception {
		String item = "$.items[?(@.caseId == " + caseId + ")]";
		mockMvc.perform(get("/api/v1/admin/cases").param("size", "100").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(100))
			.andExpect(jsonPath(item + ".status").value("DRAFT"))
			.andExpect(jsonPath(item + ".pending.aiCandidates").value(1))
			.andExpect(jsonPath(item + ".pending.courtCandidates").value(0))
			.andExpect(jsonPath(item + ".pending.axisNeedsReview").value(2))
			.andExpect(jsonPath(item + ".hasPublishedAi").value(false))
			.andExpect(jsonPath(item + ".hasPublishedCourt").value(true))
			.andExpect(jsonPath(item + ".lastActivityAt").value(hasItem(endsWith("+09:00"))));
	}

	@Test
	@DisplayName("A-5: status · pending 필터 — 공개 사건만 보면 빠지고, 대기 후보가 없는 사건만 보면 빠진다")
	void listCases_filters() throws Exception {
		mockMvc.perform(get("/api/v1/admin/cases").param("status", "PUBLISHED").param("size", "100").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].caseId").value(not(hasItem((int) caseId))));
		mockMvc.perform(get("/api/v1/admin/cases").param("pending", "false").param("size", "100").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].caseId").value(not(hasItem((int) caseId))));
		mockMvc.perform(get("/api/v1/admin/cases").param("status", "DRAFT,REVIEW").param("pending", "true")
				.param("size", "100").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].caseId").value(hasItem((int) caseId)));
	}

	@Test
	@DisplayName("A-5: 모르는 status · pending이 true/false가 아님 · page 음수 · size 범위 밖은 400")
	void listCases_invalidQuery() throws Exception {
		for (String[] query : List.of(new String[] {"status", "DONE"}, new String[] {"pending", "yes"},
			new String[] {"page", "-1"}, new String[] {"size", "0"}, new String[] {"size", "101"},
			new String[] {"size", "abc"})) {
			mockMvc.perform(get("/api/v1/admin/cases").param(query[0], query[1]).session(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
	}

	@Test
	@DisplayName("A-6: 내용 · 원본 정보(원문 제외) · 요소 · 후보 · 공개 가능 여부")
	void getCase_detail() throws Exception {
		mockMvc.perform(get("/api/v1/admin/cases/" + caseId).session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.content.chargeName").value("테스트죄"))
			.andExpect(jsonPath("$.sources[0].sourceId").value(sourceId))
			.andExpect(jsonPath("$.sources[0].caseNumber").value("2099고합000"))
			.andExpect(jsonPath("$.sources[0].hasOriginalText").value(true))
			.andExpect(jsonPath("$.sources[0].originalText").doesNotExist())
			.andExpect(jsonPath("$.factors.length()").value(2))
			.andExpect(jsonPath("$.factors[0].valueAxisStatus").value("AUTO"))
			.andExpect(jsonPath("$.candidates.AI[0].judgmentId").value(aiCandidateId))
			.andExpect(jsonPath("$.candidates.AI[0].reviewStatus").value("PENDING"))
			.andExpect(jsonPath("$.candidates.AI[0].penaltyLabel").value("징역 15년"))
			.andExpect(jsonPath("$.candidates.AI[0].modelName").value("시험 모델"))
			.andExpect(jsonPath("$.candidates.COURT[0].reviewStatus").value("APPROVED"))	// 시드처럼 공개만 된 판결 = 승인
			.andExpect(jsonPath("$.candidates.COURT[0].isPublished").value(true))
			.andExpect(jsonPath("$.publishReadiness.ready").value(false))
			.andExpect(jsonPath("$.publishReadiness.reasons.length()").value(2))
			.andExpect(jsonPath("$.publishReadiness.reasons[0]").value("NO_PUBLISHED_AI"))
			.andExpect(jsonPath("$.publishReadiness.reasons[1]").value("NO_PENALTY_RULE"));
	}

	@Test
	@DisplayName("A-6: 없는 사건은 404 CASE_NOT_FOUND")
	void getCase_unknown() throws Exception {
		mockMvc.perform(get("/api/v1/admin/cases/-1").session(session))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
	}

	@Test
	@DisplayName("A-7: 원문을 주고 열람 감사 로그를 남긴다. 다른 사건의 원문 번호면 404이고 로그도 없다")
	void getSourceText_auditsRead() throws Exception {
		mockMvc.perform(get("/api/v1/admin/cases/" + caseId + "/sources/" + sourceId).session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.originalText").value("가상 판결문 원문"));
		mockMvc.perform(get("/api/v1/admin/cases/-1/sources/" + sourceId).session(session))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
		assertThat(jdbcTemplate.queryForList("""
			SELECT target_id FROM admin_audit_log
			WHERE actor_email = ? AND action = 'SOURCE_VIEW' AND target_type = 'CASE_SOURCE'""", Long.class,
			admin.getEmail())).containsExactly(sourceId);
	}

	@Test
	@DisplayName("A-8: AI 후보는 판결 · 요소 방향 · 생성 정보, 재판부는 generation null, 사용자 판결은 404")
	void getJudgment_detail() throws Exception {
		mockMvc.perform(get("/api/v1/admin/judgments/" + aiCandidateId).session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.caseId").value(caseId))
			.andExpect(jsonPath("$.subjectType").value("AI"))
			.andExpect(jsonPath("$.reviewStatus").value("PENDING"))
			.andExpect(jsonPath("$.isPublished").value(false))
			.andExpect(jsonPath("$.judgment.prisonMonths").value(180))
			.andExpect(jsonPath("$.judgment.factors[0].factorId").value(overviewFactorId))
			.andExpect(jsonPath("$.judgment.factors[0].direction").value("UP"))
			.andExpect(jsonPath("$.generation.modelName").value("시험 모델"))
			.andExpect(jsonPath("$.generation.promptVersion").value("v-test"))
			.andExpect(jsonPath("$.generation.rawOutput").doesNotExist());
		mockMvc.perform(get("/api/v1/admin/judgments/" + courtJudgmentId).session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
			.andExpect(jsonPath("$.generation").isEmpty());

		UUID user = UUID.randomUUID();
		insertExperience(user, caseId, 1, "VERDICT_CONFIRMED");
		Long userJudgment = jdbcTemplate.queryForObject("""
			INSERT INTO judgment (case_id, subject_type, timing, experience_id, penalty_type, prison_months, is_published)
			SELECT ?, 'USER', 'FINAL', id, 'PRISON', 60, true FROM experience WHERE anonymous_user_id = ?
			RETURNING id""", Long.class, caseId, user);
		mockMvc.perform(get("/api/v1/admin/judgments/" + userJudgment).session(session))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("JUDGMENT_NOT_FOUND"));
	}

	private long judgment(String subjectType, boolean published, int prisonMonths) {
		return jdbcTemplate.queryForObject("""
			INSERT INTO judgment (case_id, subject_type, timing, penalty_type, prison_months, summary, reasoning, is_published)
			VALUES (?, ?, 'FINAL', 'PRISON', ?, '시험 요약', '시험 이유', ?) RETURNING id""",
			Long.class, caseId, subjectType, prisonMonths, published);
	}

	private MockHttpSession login(String email) throws Exception {
		Cookie csrf = mockMvc.perform(get("/api/v1/admin/auth/csrf")).andReturn().getResponse().getCookie(CSRF_COOKIE);
		return (MockHttpSession) mockMvc.perform(post("/api/v1/admin/auth/login").cookie(csrf)
				.header(CSRF_HEADER, csrf.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn().getRequest().getSession(false);
	}
}
