package com.team2.project.personality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 체험 완료(API 13 비교 공개) 때 판결 성향 기여 저장 통합 테스트 (확장 단계, BE-64 · WBS 7.6.7)
 * 기준: 성향 산식 3-1(쓰는 기록) · 3-2(방향 → 글자) · 3-4(저장 시점) · 3-5(계산 예시) · 6장(axis_counts 형식)
 */
class ExperiencePersonalityRecordApiTest extends ApiIntegrationTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private UUID anonymousId;
	private long caseId;

	@BeforeEach
	void setUpCase() {
		anonymousId = UUID.randomUUID();
		caseId = insertCase("MURDER", "PUBLISHED");
		// 비교 공개는 공개된 AI · 재판부 판결이 있어야 상태를 옮긴다 (RevealService.requirePublishedJudgments)
		insertPublishedJudgment("AI");
		insertPublishedJudgment("COURT");
	}

	// --- 3-5 계산 예시 ---

	@Test
	void revealComparison_firstTime_savesAxisCountsOfFinalJudgment() throws Exception {
		long experienceId = insertExperienceAt(1, "AI_REVEALED");
		long myFinal = insertUserFinalJudgment(experienceId);
		// 성향 산식 3-5 표 그대로: 반성 감경 · 계획성 가중 · 결과의 중대성 가중 · 합의 감경 · 피해 회복 감경 · 전과 가중
		insertJudgmentFactor(myFinal, axisFactor("반성", "APOLOGY_SINCERITY", 1), "DOWN");
		insertJudgmentFactor(myFinal, axisFactor("계획성", "FAULT_STANDARD", 2), "UP");
		insertJudgmentFactor(myFinal, axisFactor("결과의 중대성", "FAULT_STANDARD", 3), "UP");
		insertJudgmentFactor(myFinal, axisFactor("합의 · 처벌불원", "PRINCIPLE_RELATION", 4), "DOWN");
		insertJudgmentFactor(myFinal, axisFactor("피해 회복", "PRINCIPLE_RELATION", 5), "DOWN");
		insertJudgmentFactor(myFinal, axisFactor("전과", "ORDER_OPPORTUNITY", 6), "UP");
		// 축이 NULL인 요소는 성향 계산에서 빠진다 (3-1)
		insertJudgmentFactor(myFinal, axisFactor("축 없는 요소", null, 7), "UP");

		revealComparison().andExpect(jsonPath("$.status").value("COMPLETED"));

		JsonNode counts = axisCountsOf(experienceId);
		assertAxis(counts, "APOLOGY_SINCERITY", 1, 0);	// → I
		assertAxis(counts, "FAULT_STANDARD", 0, 2);		// → S
		assertAxis(counts, "PRINCIPLE_RELATION", 2, 0);	// → F
		assertAxis(counts, "ORDER_OPPORTUNITY", 0, 1);	// → J
	}

	// --- 3-1 쓰는 기록: USER · FINAL만 ---

	@Test
	void revealComparison_countsOnlyUserFinalFactors() throws Exception {
		long experienceId = insertExperienceAt(1, "AI_REVEALED");
		long apology = axisFactor("반성", "APOLOGY_SINCERITY", 1);
		long relation = axisFactor("피해 회복", "PRINCIPLE_RELATION", 2);

		// 사전 판단(PRE)은 방향이 없고 성향 기준이 아니다 → 세지 않는다
		long myPre = insertUserPreJudgment(experienceId);
		insertJudgmentFactor(myPre, apology, null);
		insertJudgmentFactor(myPre, relation, null);
		// AI · 재판부 판단의 요소는 사용자 성향이 아니다 → 세지 않는다
		insertJudgmentFactor(publishedJudgmentId("AI"), apology, "UP");
		insertJudgmentFactor(publishedJudgmentId("COURT"), relation, "UP");
		// 사용자 최종 판단에서는 반성 하나만 감경으로 골랐다
		insertJudgmentFactor(insertUserFinalJudgment(experienceId), apology, "DOWN");

		revealComparison().andExpect(status().isOk());

		JsonNode counts = axisCountsOf(experienceId);
		assertAxis(counts, "APOLOGY_SINCERITY", 1, 0);
		assertAxis(counts, "FAULT_STANDARD", 0, 0);
		assertAxis(counts, "PRINCIPLE_RELATION", 0, 0);
		assertAxis(counts, "ORDER_OPPORTUNITY", 0, 0);
	}

	@Test
	void revealComparison_noFactorsChosen_stillSavesRowWithAllAxesEmpty() throws Exception {
		long experienceId = insertExperienceAt(1, "AI_REVEALED");
		insertUserFinalJudgment(experienceId);	// 판단 요소를 하나도 고르지 않음

		revealComparison().andExpect(status().isOk());

		// 행은 남기고 네 축 모두 {down: 0, up: 0} = 자료 없음 (3-4)
		JsonNode counts = axisCountsOf(experienceId);
		for (String axis : List.of("APOLOGY_SINCERITY", "FAULT_STANDARD", "PRINCIPLE_RELATION", "ORDER_OPPORTUNITY")) {
			assertAxis(counts, axis, 0, 0);
		}
	}

	@Test
	void revealComparison_valueAxisStatusIsIgnored_autoAxisIsCounted() throws Exception {
		long experienceId = insertExperienceAt(1, "AI_REVEALED");
		long factorId = axisFactor("반성", "APOLOGY_SINCERITY", 1);
		// 관리자 확정 전(AUTO) 축도 쓴다 (3-1). 기본값이 AUTO지만 명시해 둔다
		jdbcTemplate.update("UPDATE factor SET value_axis_status = 'AUTO' WHERE id = ?", factorId);
		insertJudgmentFactor(insertUserFinalJudgment(experienceId), factorId, "UP");

		revealComparison().andExpect(status().isOk());

		assertAxis(axisCountsOf(experienceId), "APOLOGY_SINCERITY", 0, 1);
	}

	// --- 3-4 저장 시점: 처음 COMPLETED가 될 때 한 번, 고정 저장 ---

	@Test
	void revealComparison_again_keepsFirstRecordEvenIfValueAxisChanged() throws Exception {
		long experienceId = insertExperienceAt(1, "AI_REVEALED");
		long factorId = axisFactor("반성", "APOLOGY_SINCERITY", 1);
		insertJudgmentFactor(insertUserFinalJudgment(experienceId), factorId, "DOWN");
		revealComparison().andExpect(status().isOk());

		// 나중에 관리자가 축을 바꾼 뒤 비교 공개를 다시 보내도(멱등) 다시 세지 않는다
		jdbcTemplate.update("UPDATE factor SET value_axis = 'ORDER_OPPORTUNITY' WHERE id = ?", factorId);
		revealComparison().andExpect(jsonPath("$.status").value("COMPLETED"));

		assertThat(countContributions(experienceId)).isEqualTo(1);
		JsonNode counts = axisCountsOf(experienceId);
		assertAxis(counts, "APOLOGY_SINCERITY", 1, 0);	// 처음 값 그대로
		assertAxis(counts, "ORDER_OPPORTUNITY", 0, 0);
	}

	@Test
	void revealComparison_alreadyCompletedBeforeThisFeature_doesNotBackfill() throws Exception {
		// BE-64 이전에 이미 COMPLETED가 된 체험: 다시 공개를 보내도 상태가 바뀌지 않으므로 저장하지 않는다
		// (P-5 experienceCount가 "성향 기여가 저장된 체험 수"인 이유, #128)
		long experienceId = insertExperienceAt(1, "COMPLETED");
		insertJudgmentFactor(insertUserFinalJudgment(experienceId), axisFactor("반성", "APOLOGY_SINCERITY", 1), "DOWN");

		revealComparison().andExpect(jsonPath("$.status").value("COMPLETED"));

		assertThat(countContributions(experienceId)).isZero();
	}

	@Test
	void revealComparison_secondAttempt_isNotSaved() throws Exception {
		// 첫 회차는 이미 완료(기여 없음), 두 번째 체험을 지금 완료한다
		insertExperienceAt(1, "COMPLETED");
		long secondAttempt = insertExperienceAt(2, "AI_REVEALED");
		insertJudgmentFactor(insertUserFinalJudgment(secondAttempt), axisFactor("반성", "APOLOGY_SINCERITY", 1), "DOWN");

		revealComparison().andExpect(jsonPath("$.status").value("COMPLETED"));

		// 두 번째 체험부터는 실제 판결을 이미 알고 있어 성향 통계에서 뺀다 (3-1, ERD 결정 #5)
		assertThat(statusOf(secondAttempt)).isEqualTo("COMPLETED");
		assertThat(countContributions(secondAttempt)).isZero();
	}

	@Test
	void revealComparison_withoutPersonalityTest_stillSaves() throws Exception {
		// 성향 테스트(personality_result)를 하지 않은 사용자도 저장한다. 나중에 테스트를 하면 바로 종합에 들어간다 (3-4)
		long experienceId = insertExperienceAt(1, "AI_REVEALED");
		insertUserFinalJudgment(experienceId);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM personality_result WHERE anonymous_user_id = ?", Integer.class, anonymousId)).isZero();

		revealComparison().andExpect(status().isOk());

		assertThat(countContributions(experienceId)).isEqualTo(1);
	}

	// --- 테스트 데이터 ---

	private ResultActions revealComparison() throws Exception {
		return mockMvc.perform(post(experienceUrl(caseId) + "/comparison-reveal")
			.cookie(new Cookie(COOKIE, anonymousId.toString())));
	}

	private long insertExperienceAt(int attemptNo, String status) {
		insertExperience(anonymousId, caseId, attemptNo, status);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM experience WHERE case_id = ? AND anonymous_user_id = ? AND attempt_no = ?",
			Long.class, caseId, anonymousId, attemptNo);
	}

	/** 판단 요소. valueAxis가 null이면 어느 축에도 맞지 않는 요소 */
	private long axisFactor(String label, String valueAxis, int displayOrder) {
		long id = insertFactor(caseId, "DETAIL", label, label, displayOrder);
		jdbcTemplate.update("UPDATE factor SET value_axis = ? WHERE id = ?", valueAxis, id);
		return id;
	}

	private void insertPublishedJudgment(String subjectType) {
		jdbcTemplate.update("""
				INSERT INTO judgment (case_id, subject_type, timing, penalty_type, prison_months, summary, reasoning, is_published)
				VALUES (?, ?, 'FINAL', 'PRISON', 120, '한 줄 요약', '판결 이유', true)
				""", caseId, subjectType);
	}

	private long publishedJudgmentId(String subjectType) {
		return jdbcTemplate.queryForObject(
			"SELECT id FROM judgment WHERE case_id = ? AND subject_type = ? AND is_published", Long.class, caseId, subjectType);
	}

	private long insertUserFinalJudgment(long experienceId) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO judgment (case_id, subject_type, timing, experience_id, penalty_type, prison_months, is_published)
				VALUES (?, 'USER', 'FINAL', ?, 'PRISON', 180, true)
				RETURNING id
				""", Long.class, caseId, experienceId);
	}

	private long insertUserPreJudgment(long experienceId) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO judgment (case_id, subject_type, timing, experience_id, range_option_id, is_published)
				VALUES (?, 'USER', 'PRE', ?, ?, true)
				RETURNING id
				""", Long.class, caseId, experienceId, rangeOptionIds("MURDER").get(0));
	}

	private void insertJudgmentFactor(long judgmentId, long factorId, String direction) {
		jdbcTemplate.update("INSERT INTO judgment_factor (judgment_id, factor_id, direction) VALUES (?, ?, ?)",
			judgmentId, factorId, direction);
	}

	private int countContributions(long experienceId) {
		return jdbcTemplate.queryForObject(
			"SELECT count(*) FROM experience_personality WHERE experience_id = ?", Integer.class, experienceId);
	}

	private JsonNode axisCountsOf(long experienceId) {
		assertThat(countContributions(experienceId)).isEqualTo(1);
		String json = jdbcTemplate.queryForObject(
			"SELECT axis_counts::text FROM experience_personality WHERE experience_id = ?", String.class, experienceId);
		return JSON.readTree(json);
	}

	private String statusOf(long experienceId) {
		return jdbcTemplate.queryForObject("SELECT status FROM experience WHERE id = ?", String.class, experienceId);
	}

	private static void assertAxis(JsonNode counts, String axis, int down, int up) {
		assertThat(counts.path(axis).path("down").asInt(-1)).as(axis + ".down").isEqualTo(down);
		assertThat(counts.path(axis).path("up").asInt(-1)).as(axis + ".up").isEqualTo(up);
	}
}
