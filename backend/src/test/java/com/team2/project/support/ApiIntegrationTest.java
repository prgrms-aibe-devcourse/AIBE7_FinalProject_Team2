package com.team2.project.support;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

/**
 * 실제 PostgreSQL(로컬 docker-compose / CI 서비스 컨테이너)에 붙는 API 통합 테스트 공통 부모.
 * 시드 데이터가 이미 들어 있을 수 있으므로, 각 테스트는 자기가 만든 사건만 검증하고 끝나면 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiIntegrationTest {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	protected static final String COOKIE = "NLNB_AID";

	private final List<Long> createdCaseIds = new ArrayList<>();

	protected long insertCase(String crimeType, String status) {
		Long id = jdbcTemplate.queryForObject("""
				INSERT INTO legal_case (title, crime_type, charge_name, short_intro, keywords, difficulty,
				                        estimated_minutes, overview, applied_law, statutory_penalty_text,
				                        status, published_at)
				VALUES (?, ?, '테스트죄', '테스트 소개', '["키워드1", "키워드2"]'::jsonb, 'MID',
				        10, '테스트 개요', '테스트 법조문', '테스트 법정형',
				        ?, CASE WHEN ? = 'PUBLISHED' THEN now() END)
				RETURNING id
				""", Long.class, "테스트 사건 " + UUID.randomUUID(), crimeType, status, status);
		createdCaseIds.add(id);
		return id;
	}

	protected long insertFactor(long caseId, String revealStage, String label, String preLabel, int displayOrder) {
		Long id = jdbcTemplate.queryForObject("""
				INSERT INTO factor (case_id, label, pre_label, reveal_stage, summary_tag, display_order)
				VALUES (?, ?, ?, ?, '테스트', ?)
				RETURNING id
				""", Long.class, caseId, label, preLabel, revealStage, displayOrder);
		return id;
	}

	/** 범죄 유형의 사전 판단 형량 구간 ID (V1 마이그레이션이 넣은 고정 데이터), 표시 순서대로. */
	protected List<Long> rangeOptionIds(String crimeType) {
		return jdbcTemplate.queryForList(
				"SELECT id FROM sentence_range_option WHERE crime_type = ? ORDER BY display_order", Long.class, crimeType);
	}

	/** 쿠키 없이 체험을 시작해 발급된 익명 ID 쿠키를 돌려준다. */
	protected Cookie startAndGetCookie(long caseId) throws Exception {
		MockHttpServletResponse response = mockMvc.perform(post(experienceUrl(caseId))).andExpect(status().isCreated())
				.andReturn().getResponse();
		String value = response.getHeader(HttpHeaders.SET_COOKIE).split(";")[0].substring((COOKIE + "=").length());
		return new Cookie(COOKIE, value);
	}

	protected static String experienceUrl(long caseId) {
		return "/api/v1/cases/" + caseId + "/experience";
	}

	protected void insertExperience(UUID anonymousUserId, long caseId, int attemptNo, String status) {
		jdbcTemplate.update("INSERT INTO anonymous_user (id) VALUES (?) ON CONFLICT DO NOTHING", anonymousUserId);
		jdbcTemplate.update("INSERT INTO experience (anonymous_user_id, case_id, attempt_no, status) VALUES (?, ?, ?, ?)",
				anonymousUserId, caseId, attemptNo, status);
	}

	protected int countExperiences(long caseId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM experience WHERE case_id = ?", Integer.class, caseId);
	}

	@AfterEach
	void deleteCreatedData() {
		// 판단 → 체험 → 사용자 → 요소 · 사건 순서로 지운다 (한 사용자가 여러 사건에 체험을 가질 수 있어 사용자는 마지막에 한꺼번에)
		Set<UUID> userIds = new HashSet<>();
		for (Long caseId : createdCaseIds) {
			jdbcTemplate.update(
					"DELETE FROM judgment_factor WHERE judgment_id IN (SELECT id FROM judgment WHERE case_id = ?)", caseId);
			jdbcTemplate.update("DELETE FROM judgment WHERE case_id = ?", caseId);
			userIds.addAll(jdbcTemplate.queryForList(
					"SELECT anonymous_user_id FROM experience WHERE case_id = ?", UUID.class, caseId));
			jdbcTemplate.update("DELETE FROM experience WHERE case_id = ?", caseId);
		}
		userIds.forEach(id -> jdbcTemplate.update("DELETE FROM anonymous_user WHERE id = ?", id));
		for (Long caseId : createdCaseIds) {
			jdbcTemplate.update("DELETE FROM factor WHERE case_id = ?", caseId);
			jdbcTemplate.update("DELETE FROM legal_case WHERE id = ?", caseId);
		}
		createdCaseIds.clear();
	}
}
