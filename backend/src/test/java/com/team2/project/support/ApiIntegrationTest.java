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
	private final List<Long> createdMemberIds = new ArrayList<>();

	/**
	 * 회원 한 명(카카오, 닉네임 없음). 끝나면 회원 키 데이터(member_* — 회원 삭제로 함께 지워짐) · 체험 · 성향 결과 · 커뮤니티 글과 함께 지운다.
	 * 인증은 시험용 토큰 "member-{id}"(FakeMemberTokens)이므로 쓰는 시험 클래스에 {@code @Import(FakeMemberTokens.class)}를 붙인다 (BE-140)
	 */
	protected long registerMember() {
		Long id = jdbcTemplate.queryForObject(
				"INSERT INTO member (provider, provider_user_id) VALUES ('KAKAO', ?) RETURNING id", Long.class,
				UUID.randomUUID().toString());
		createdMemberIds.add(id);
		return id;
	}

	/** 그 회원의 Authorization 헤더 값 (FakeMemberTokens) */
	protected static String bearer(long memberId) {
		return FakeMemberTokens.bearer(memberId);
	}

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

	/** 대본(BE-108) 한 건. reviewStatus가 null이면 검수 대기. 내용은 비워 두고 "승인 여부"만 본다 */
	protected void insertScriptWithReview(long caseId, String reviewStatus) {
		jdbcTemplate.update("""
				INSERT INTO case_script (case_id, evidence, model, generated_at, stopped, cast_roles, publishable,
				                         review_status)
				VALUES (?, '[]'::jsonb, 'test', now(), false, '{}'::jsonb, true, ?)
				""", caseId, reviewStatus);
	}

	/** 신문 지면(BE-92). 사건당 1건 */
	protected void insertCasePaper(long caseId, String headline) {
		jdbcTemplate.update("""
				INSERT INTO case_paper (case_id, desk, headline, deck, caption, article)
				VALUES (?, '사회부', ?, '테스트 부제', '테스트 캡션', '["문단1"]'::jsonb)
				""", caseId, headline);
	}

	/** 발단 · 결론 사진(BE-91, 역할 칸 BE-101). role은 "ORIGIN" · "CONCLUSION" */
	protected void insertIncidentPhoto(long caseId, String role, String imageUrl, String caption) {
		int displayOrder = "ORIGIN".equals(role) ? 0 : 1;
		jdbcTemplate.update("""
				INSERT INTO case_incident_photo (case_id, image_url, s3_key, caption, display_order, role)
				VALUES (?, ?, ?, ?, ?, ?)
				""", caseId, imageUrl, "test-key-" + UUID.randomUUID(), caption, displayOrder, role);
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
			// 비교 공개(API 13)가 체험 성향 기여를 저장하므로(BE-64) 체험보다 먼저 지운다 (FK)
			jdbcTemplate.update(
					"DELETE FROM experience_personality WHERE experience_id IN (SELECT id FROM experience WHERE case_id = ?)",
					caseId);
			jdbcTemplate.update("DELETE FROM experience WHERE case_id = ?", caseId);
		}
		userIds.forEach(id -> {
			jdbcTemplate.update("DELETE FROM personality_result WHERE anonymous_user_id = ?", id);
			jdbcTemplate.update("DELETE FROM anonymous_user WHERE id = ?", id);
		});
		for (Long caseId : createdCaseIds) {
			jdbcTemplate.update("DELETE FROM factor WHERE case_id = ?", caseId);
			jdbcTemplate.update("DELETE FROM case_source WHERE case_id = ?", caseId);
			jdbcTemplate.update("DELETE FROM case_paper WHERE case_id = ?", caseId);	// 신문 지면(BE-92, FK)
			jdbcTemplate.update("DELETE FROM case_script WHERE case_id = ?", caseId);	// 사건 대본(BE-108, FK)
			jdbcTemplate.update("DELETE FROM case_incident_photo WHERE case_id = ?", caseId);	// 발단 · 결론 사진(BE-91, FK)
			jdbcTemplate.update("DELETE FROM legal_case WHERE id = ?", caseId);
		}
		createdCaseIds.clear();
		// 회원 키 데이터(member_* 7개 · 가져오기 결정)는 회원 삭제로 함께 지워진다(ON DELETE CASCADE). 아닌 것을 먼저 지운다 (BE-140)
		createdMemberIds.forEach(id -> {
			jdbcTemplate.update("DELETE FROM community_like WHERE member_id = ?", id);
			jdbcTemplate.update("DELETE FROM community_comment WHERE member_id = ?", id);
			jdbcTemplate.update("DELETE FROM community_post WHERE member_id = ?", id);	// 글의 댓글 · 좋아요는 함께 지워진다
			jdbcTemplate.update("DELETE FROM personality_result WHERE member_id = ?", id);
			jdbcTemplate.update("DELETE FROM experience_personality WHERE experience_id IN (SELECT id FROM experience WHERE member_id = ?)", id);
			jdbcTemplate.update("DELETE FROM experience WHERE member_id = ?", id);
			jdbcTemplate.update("DELETE FROM member WHERE id = ?", id);
		});
		createdMemberIds.clear();
	}
}
