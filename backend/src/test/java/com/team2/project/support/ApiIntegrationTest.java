package com.team2.project.support;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

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
		// 한 사용자가 여러 사건에 체험을 가질 수 있으므로 체험 → 사용자 → 사건 순서로 한꺼번에 지운다
		Set<UUID> userIds = new HashSet<>();
		for (Long caseId : createdCaseIds) {
			userIds.addAll(jdbcTemplate.queryForList(
					"SELECT anonymous_user_id FROM experience WHERE case_id = ?", UUID.class, caseId));
			jdbcTemplate.update("DELETE FROM experience WHERE case_id = ?", caseId);
		}
		userIds.forEach(id -> jdbcTemplate.update("DELETE FROM anonymous_user WHERE id = ?", id));
		createdCaseIds.forEach(caseId -> jdbcTemplate.update("DELETE FROM legal_case WHERE id = ?", caseId));
		createdCaseIds.clear();
	}
}
