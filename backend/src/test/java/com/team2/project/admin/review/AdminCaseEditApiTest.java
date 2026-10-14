package com.team2.project.admin.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 사건 내용 수정 · 가치관 축 확정 · 사건 공개 A-11 ~ A-13 (API 명세 8-2, BE-43). 실제 로그인 세션 + CSRF 토큰.
 */
class AdminCaseEditApiTest extends ApiIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";
	private static final String CSRF_COOKIE = "XSRF-TOKEN";

	@Autowired AdminAccountRepository accountRepository;
	@Autowired PasswordEncoder passwordEncoder;

	private AdminAccount admin;
	private MockHttpSession session;
	private Cookie csrf;
	private long caseId;
	private long overviewFactorId;

	@BeforeEach
	void setUp() throws Exception {
		admin = accountRepository.save(AdminAccount.withPassword("edit-" + UUID.randomUUID() + "@example.com",
			passwordEncoder.encode(PASSWORD), "수정 테스트"));
		login();
		caseId = insertCase("MURDER", "DRAFT");
		overviewFactorId = insertFactor(caseId, "OVERVIEW", "다툼 끝에 일어났다", "다투다 일어났다", 1);
		jdbcTemplate.update("UPDATE legal_case SET recommended_min_months = 120, recommended_max_months = 192 WHERE id = ?",
			caseId);
	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM penalty_rule WHERE case_id = ?", caseId);
		jdbcTemplate.update("DELETE FROM admin_audit_log WHERE actor_email = ?", admin.getEmail());
		accountRepository.deleteById(admin.getId());
	}

	// --- A-11 ---

	@Test
	@DisplayName("A-11: 보낸 필드만 바꾸고 null은 지운다, 사건 DRAFT → REVIEW, 감사 로그에는 바꾼 필드 이름만")
	void edit_partialAndNull() throws Exception {
		jdbcTemplate.update("UPDATE legal_case SET thumbnail_url = '/old.webp', difficulty = 'MID' WHERE id = ?", caseId);
		perform(patch(caseUrl()).contentType(MediaType.APPLICATION_JSON).content("""
				{"shortIntro": "고친 소개", "keywords": ["금전 갈등", "지인"], "thumbnailUrl": null, "estimatedMinutes": "15"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REVIEW"))
			.andExpect(jsonPath("$.content.shortIntro").value("고친 소개"))
			.andExpect(jsonPath("$.content.keywords[1]").value("지인"))
			.andExpect(jsonPath("$.content.thumbnailUrl").isEmpty())
			.andExpect(jsonPath("$.content.estimatedMinutes").value(15))
			.andExpect(jsonPath("$.content.difficulty").value("MID"))		// 보내지 않은 필드는 그대로
			.andExpect(jsonPath("$.content.overview").value("테스트 개요"));
		assertThat(jdbcTemplate.queryForObject("""
			SELECT detail->>'fields' FROM admin_audit_log WHERE actor_email = ? AND action = 'CASE_UPDATE'""",
			String.class, admin.getEmail())).contains("shortIntro", "keywords", "thumbnailUrl", "estimatedMinutes")
			.doesNotContain("고친 소개");
	}

	@Test
	@DisplayName("A-11: 권고 하한 · 상한은 하나만 보내도 저장된 값과 비교한다")
	void edit_recommendedRange() throws Exception {
		perform(patch(caseUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"recommendedMinMonths\": 200}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.details[0].field").value("recommendedMinMonths"));
		perform(patch(caseUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"recommendedMaxMonths\": null, \"recommendedMinMonths\": 200}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.recommendedMinMonths").value(200))
			.andExpect(jsonPath("$.content.recommendedMaxMonths").isEmpty());
	}

	@Test
	@DisplayName("A-11: 규칙 위반 · 필수 필드 null · keywords null · 모르는 필드 · 빈 본문 · 소수는 400")
	void edit_invalidBodies() throws Exception {
		for (String body : List.of("{}", "{\"title\": null}", "{\"title\": \"  \"}", "{\"title\": \"" + "가".repeat(101) + "\"}",
			"{\"shortIntro\": \"" + "가".repeat(201) + "\"}", "{\"overview\": null}", "{\"keywords\": null}",
			"{\"keywords\": [\"a\", null]}", "{\"difficulty\": \"HARD\"}", "{\"estimatedMinutes\": 0}",
			"{\"estimatedMinutes\": 1.5}", "{\"chargeName\": \"상해\"}", "{\"thumbnailUrl\": \"" + "a".repeat(301) + "\"}")) {
			perform(patch(caseUrl()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		assertThat(caseStatus()).isEqualTo("DRAFT");	// 아무것도 바뀌지 않았다
	}

	@Test
	@DisplayName("A-11: 다른 사건과 제목이 겹치면 409 DUPLICATE_TITLE, 공개한 사건은 409 CASE_PUBLISHED, 없는 사건은 404")
	void edit_conflicts() throws Exception {
		String otherTitle = jdbcTemplate.queryForObject("SELECT title FROM legal_case WHERE id = ?", String.class,
			insertCase("FRAUD", "DRAFT"));
		perform(patch(caseUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"" + otherTitle + "\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_TITLE"));

		jdbcTemplate.update("UPDATE legal_case SET status = 'PUBLISHED', published_at = now() WHERE id = ?", caseId);
		perform(patch(caseUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"shortIntro\": \"고침\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.details[0].reason").value("CASE_PUBLISHED"));

		perform(patch("/api/v1/admin/cases/-1").contentType(MediaType.APPLICATION_JSON).content("{\"shortIntro\": \"고침\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
	}

	// --- A-12 ---

	@Test
	@DisplayName("A-12: 축을 확정하면 CONFIRMED, null도 확정으로 본다. 공개한 사건에서도 된다")
	void confirmValueAxis() throws Exception {
		perform(put(axisUrl(overviewFactorId)).contentType(MediaType.APPLICATION_JSON).content("{\"valueAxis\": \"PRINCIPLE_RELATION\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.factorId").value(overviewFactorId))
			.andExpect(jsonPath("$.valueAxis").value("PRINCIPLE_RELATION"))
			.andExpect(jsonPath("$.valueAxisStatus").value("CONFIRMED"));
		assertThat(caseStatus()).isEqualTo("REVIEW");

		jdbcTemplate.update("UPDATE legal_case SET status = 'PUBLISHED', published_at = now() WHERE id = ?", caseId);
		perform(put(axisUrl(overviewFactorId)).contentType(MediaType.APPLICATION_JSON).content("{\"valueAxis\": null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.valueAxis").isEmpty())
			.andExpect(jsonPath("$.valueAxisStatus").value("CONFIRMED"));
		assertThat(caseStatus()).isEqualTo("PUBLISHED");
		assertThat(jdbcTemplate.queryForList("""
			SELECT action FROM admin_audit_log WHERE actor_email = ? AND action = 'VALUE_AXIS_CONFIRM'""", String.class,
			admin.getEmail())).hasSize(2);
	}

	@Test
	@DisplayName("A-12: valueAxis 키 없음 · 모르는 축은 400, 없는 요소는 404 FACTOR_NOT_FOUND")
	void confirmValueAxis_invalid() throws Exception {
		for (String body : List.of("{}", "{\"axis\": \"FAULT_STANDARD\"}", "{\"valueAxis\": \"SOMETHING\"}")) {
			perform(put(axisUrl(overviewFactorId)).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		perform(put(axisUrl(-1)).contentType(MediaType.APPLICATION_JSON).content("{\"valueAxis\": null}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("FACTOR_NOT_FOUND"));
	}

	// --- A-13 ---

	@Test
	@DisplayName("A-13: 공개 조건을 채우지 않으면 409 PUBLISH_NOT_READY와 사유 목록, 채우면 PUBLISHED · published_at · 감사 로그")
	void publish() throws Exception {
		perform(post(caseUrl() + "/publish"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PUBLISH_NOT_READY"))
			.andExpect(jsonPath("$.details.length()").value(3))
			.andExpect(jsonPath("$.details[0].reason").value("NO_PUBLISHED_AI"))
			.andExpect(jsonPath("$.details[1].reason").value("NO_PUBLISHED_COURT"))
			.andExpect(jsonPath("$.details[2].reason").value("NO_PENALTY_RULE"));

		judgment("AI");
		judgment("COURT");
		jdbcTemplate.update("""
			INSERT INTO penalty_rule (case_id, penalty_type, allowed_min, allowed_max, suspension_allowed, display_order)
			VALUES (?, 'PRISON', 60, 360, true, 1)""", caseId);
		perform(post(caseUrl() + "/publish"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.publishReadiness.ready").value(true));
		assertThat(jdbcTemplate.queryForObject("SELECT published_at IS NOT NULL FROM legal_case WHERE id = ?", Boolean.class,
			caseId)).isTrue();
		assertThat(jdbcTemplate.queryForList("""
			SELECT action FROM admin_audit_log WHERE actor_email = ? AND action = 'CASE_PUBLISH'""", String.class,
			admin.getEmail())).hasSize(1);

		perform(post(caseUrl() + "/publish"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.details[0].reason").value("CASE_PUBLISHED"));
	}

	@Test
	@DisplayName("상태를 바꾸는 요청은 CSRF 토큰이 없으면 403")
	void csrfRequired() throws Exception {
		mockMvc.perform(post(caseUrl() + "/publish").session(session)).andExpect(status().isForbidden());
		mockMvc.perform(patch(caseUrl()).session(session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"shortIntro\": \"고침\"}")).andExpect(status().isForbidden());
		assertThat(caseStatus()).isEqualTo("DRAFT");
	}

	// --- 도움 함수 ---

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.session(session).cookie(csrf).header(CSRF_HEADER, csrf.getValue()));
	}

	private String caseUrl() {
		return "/api/v1/admin/cases/" + caseId;
	}

	private static String axisUrl(long factorId) {
		return "/api/v1/admin/factors/" + factorId + "/value-axis";
	}

	private void judgment(String subjectType) {
		jdbcTemplate.update("""
			INSERT INTO judgment (case_id, subject_type, timing, penalty_type, prison_months, is_published)
			VALUES (?, ?, 'FINAL', 'PRISON', 144, true)""", caseId, subjectType);
	}

	private String caseStatus() {
		return jdbcTemplate.queryForObject("SELECT status FROM legal_case WHERE id = ?", String.class, caseId);
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
		csrf = result.getResponse().getCookie(CSRF_COOKIE);
	}
}
