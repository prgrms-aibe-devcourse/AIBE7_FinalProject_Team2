package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * API 14. 세 판결 비교 통합 테스트 (S-09)
 * ERD 6장 예시 데이터(가상 살인 사건)와 같은 조합을 쓴다: 사전 판단 구간 "실형 5년 이상 ~ 10년 미만"(60 ~ 120개월),
 * 최종 판결 180개월(15년) → HEAVIER.
 */
class ComparisonApiTest extends ApiIntegrationTest {

	private UUID anonymousId;
	private long caseId;
	private long weaponFactorId;
	private long depositFactorId;
	private long preRangeOptionId;

	@BeforeEach
	void setUpCase() {
		anonymousId = UUID.randomUUID();
		caseId = insertCase("MURDER", "PUBLISHED");
		weaponFactorId = insertFactor(caseId, "OVERVIEW", "다투던 중 집에 있던 흉기를 집어 들었다",
			"다투던 중 흉기를 집어 들었다", 1);
		depositFactorId = insertFactor(caseId, "DETAIL", "피해 회복을 위해 5,000만 원을 공탁했다", null, 2);
		jdbcTemplate.update("UPDATE factor SET summary_tag = ? WHERE id = ?", "범행 방식", weaponFactorId);
		jdbcTemplate.update("UPDATE factor SET summary_tag = ? WHERE id = ?", "피해 회복", depositFactorId);
		// "실형 5년 이상 ~ 10년 미만"(60 ~ 120개월), MURDER crime_type 구간의 4번째(SUSPENDED, <3년, 3~5년 다음)
		preRangeOptionId = rangeOptionIds("MURDER").get(3);

		insertAiJudgment();
		insertCourtJudgment();
	}

	/** 검수를 마친 공개 AI 판결 (징역 12년, 흉기 요소만 고려) */
	private void insertAiJudgment() {
		long id = jdbcTemplate.queryForObject("""
				INSERT INTO judgment (case_id, subject_type, timing, penalty_type, prison_months, is_published)
				VALUES (?, 'AI', 'FINAL', 'PRISON', 144, true)
				RETURNING id
				""", Long.class, caseId);
		insertJudgmentFactor(id, weaponFactorId, "UP");
	}

	/** 검수를 마친 공개 재판부 판결 (징역 10년, 흉기 · 공탁 요소 고려) */
	private void insertCourtJudgment() {
		long id = jdbcTemplate.queryForObject("""
				INSERT INTO judgment (case_id, subject_type, timing, penalty_type, prison_months, is_published)
				VALUES (?, 'COURT', 'FINAL', 'PRISON', 120, true)
				RETURNING id
				""", Long.class, caseId);
		insertJudgmentFactor(id, weaponFactorId, "UP");
		insertJudgmentFactor(id, depositFactorId, "DOWN");
	}

	private void insertJudgmentFactor(long judgmentId, long factorId, String direction) {
		jdbcTemplate.update(
			"INSERT INTO judgment_factor (judgment_id, factor_id, direction) VALUES (?, ?, ?)",
			judgmentId, factorId, direction);
	}

	/** COMPLETED 상태의 체험 1건 + 사전 판단(흉기 요소 선택) + 최종 판결(징역 15년, 흉기 · 공탁 요소) */
	private Cookie startCompletedExperience() {
		insertExperience(anonymousId, caseId, 1, "COMPLETED");
		long experienceId = jdbcTemplate.queryForObject(
			"SELECT id FROM experience WHERE case_id = ? AND anonymous_user_id = ?", Long.class, caseId, anonymousId);

		long preJudgmentId = jdbcTemplate.queryForObject("""
				INSERT INTO judgment (case_id, subject_type, timing, experience_id, range_option_id, is_published)
				VALUES (?, 'USER', 'PRE', ?, ?, true)
				RETURNING id
				""", Long.class, caseId, experienceId, preRangeOptionId);
		jdbcTemplate.update("INSERT INTO judgment_factor (judgment_id, factor_id) VALUES (?, ?)",
			preJudgmentId, weaponFactorId);

		long finalJudgmentId = jdbcTemplate.queryForObject("""
				INSERT INTO judgment (case_id, subject_type, timing, experience_id, penalty_type, prison_months, is_published)
				VALUES (?, 'USER', 'FINAL', ?, 'PRISON', 180, true)
				RETURNING id
				""", Long.class, caseId, experienceId);
		insertJudgmentFactor(finalJudgmentId, weaponFactorId, "UP");
		insertJudgmentFactor(finalJudgmentId, depositFactorId, "DOWN");

		return new Cookie(COOKIE, anonymousId.toString());
	}

	private String url() {
		return experienceUrl(caseId) + "/comparison";
	}

	@Test
	void getComparison_afterCompleted_returnsPreToFinalMatrixAndRuleSentences() throws Exception {
		Cookie cookie = startCompletedExperience();

		mockMvc.perform(get(url()).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.preToFinal.preJudgment.rangeOptionId").value(preRangeOptionId))
			.andExpect(jsonPath("$.preToFinal.preJudgment.label").value("실형 5년 이상 ~ 10년 미만"))
			.andExpect(jsonPath("$.preToFinal.preJudgment.factorIds[0]").value(weaponFactorId))
			.andExpect(jsonPath("$.preToFinal.finalJudgmentText").value("징역 15년"))
			.andExpect(jsonPath("$.preToFinal.direction").value("HEAVIER"))
			.andExpect(jsonPath("$.judgments.USER.prisonMonths").value(180))
			.andExpect(jsonPath("$.judgments.AI.prisonMonths").value(144))
			.andExpect(jsonPath("$.judgments.COURT.prisonMonths").value(120))
			.andExpect(jsonPath("$.analysisAvailable").value(false));
	}

	@Test
	void getComparison_classifiesMatrixRowsByAgreement() throws Exception {
		Cookie cookie = startCompletedExperience();

		// 매트릭스는 display_order 순이라 흉기(1) 다음이 공탁(2)이다
		mockMvc.perform(get(url()).cookie(cookie))
			.andExpect(status().isOk())
			// 흉기 요소: 셋 다 UP
			.andExpect(jsonPath("$.matrix[0].factorId").value(weaponFactorId))
			.andExpect(jsonPath("$.matrix[0].category").value("ALL_SAME"))
			// 공탁 요소: 나와 재판부는 DOWN, AI는 고려하지 않음 → ONLY_ME_MISSED가 아니라 DIVERGED(사용자 쪽이 아닌 AI 쪽 결측)
			.andExpect(jsonPath("$.matrix[1].factorId").value(depositFactorId))
			.andExpect(jsonPath("$.matrix[1].category").value("DIVERGED"));
	}

	@Test
	void getComparison_buildsRuleSentenceFromSummaryTagForAllSameFactor() throws Exception {
		Cookie cookie = startCompletedExperience();

		// 규칙 문장은 라벨(완전한 서술문)이 아니라 요약 태그("범행 방식")로 만든다 (BE-26 확정)
		mockMvc.perform(get(url()).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ruleSentences.common[0]")
				.value("세 판결 모두 범행 방식을 형량을 높이는 요소로 봤어요."))
			.andExpect(jsonPath("$.ruleSentences.differences").isNotEmpty())
			.andExpect(jsonPath("$.matrix[0].summaryTag").doesNotExist());
	}

	@Test
	void getComparison_beforeCompleted_returns409WithCurrentStatus() throws Exception {
		insertExperience(anonymousId, caseId, 1, "AI_REVEALED");
		Cookie cookie = new Cookie(COOKIE, anonymousId.toString());

		mockMvc.perform(get(url()).cookie(cookie))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.currentStatus").value("AI_REVEALED"));
	}

	@Test
	void getComparison_withoutCookie_returns404ExperienceNotFound() throws Exception {
		startCompletedExperience();

		mockMvc.perform(get(url()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("EXPERIENCE_NOT_FOUND"));
	}

	@Test
	void getComparison_doesNotChangeStatus() throws Exception {
		Cookie cookie = startCompletedExperience();

		mockMvc.perform(get(url()).cookie(cookie)).andExpect(status().isOk());

		String status = jdbcTemplate.queryForObject(
			"SELECT status FROM experience WHERE case_id = ? AND anonymous_user_id = ?",
			String.class, caseId, anonymousId);
		assertThat(status).isEqualTo("COMPLETED");
	}
}
