package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * API 10 ~ 13 통합 테스트 (결과 공개 · 조회)
 * 공개(POST)는 다시 보내도 성공하고, 조회(GET)는 상태를 바꾸지 않는다 (API 명세 2장 · 시퀀스 7장).
 */
class JudgmentResultApiTest extends ApiIntegrationTest {

	private static final String SOURCE_ORG = "법원 판결서 인터넷열람 서비스";
	private static final String SECRET_CASE_NUMBER = "2024고합1234";
	private static final String SECRET_COURT_NAME = "서울중앙지방법원";

	private UUID anonymousId;
	private long caseId;
	private long weaponFactorId;
	private long depositFactorId;

	@BeforeEach
	void setUpCase() {
		anonymousId = UUID.randomUUID();
		caseId = insertCase("MURDER", "PUBLISHED");
		jdbcTemplate.update("UPDATE legal_case SET deidentified_items = ?::jsonb WHERE id = ?",
			"[\"인명\", \"지명\"]", caseId);
		weaponFactorId = insertFactorWithTag("OVERVIEW", "다투던 중 흉기를 집어 들었다", "흉기 사용", 1);
		depositFactorId = insertFactorWithTag("DETAIL", "피해 회복을 위해 공탁했다", "피해 회복 공탁", 2);
		jdbcTemplate.update("""
				INSERT INTO case_source (case_id, court_level, case_number, court_name, is_final, source_org)
				VALUES (?, 'FIRST', ?, ?, true, ?)
				""", caseId, SECRET_CASE_NUMBER, SECRET_COURT_NAME, SOURCE_ORG);
		// 공개 사건에는 검수를 마친 AI · 재판부 판결이 등록돼 있다 (없는 경우는 따로 지우고 검증한다)
		insertAiJudgment();
		insertCourtJudgment();
	}

	private long insertFactorWithTag(String revealStage, String label, String summaryTag, int displayOrder) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO factor (case_id, label, pre_label, reveal_stage, summary_tag, display_order)
				VALUES (?, ?, ?, ?, ?, ?)
				RETURNING id
				""", Long.class, caseId, label, label, revealStage, summaryTag, displayOrder);
	}

	/** 검수를 마친 공개 AI 판결 (징역 12년) */
	private void insertAiJudgment() {
		long id = jdbcTemplate.queryForObject("""
				INSERT INTO judgment (case_id, subject_type, timing, penalty_type, prison_months,
				                      summary, reasoning, reference_tags, is_published)
				VALUES (?, 'AI', 'FINAL', 'PRISON', 144, ?, ?, ?::jsonb, true)
				RETURNING id
				""", Long.class, caseId, "AI 한 줄 요약", "AI 판결 이유", "[\"형법 제250조\", \"살인범죄 양형기준\"]");
		insertJudgmentFactor(id, weaponFactorId, "UP", null);
	}

	/** 검수를 마친 공개 재판부 판결 (징역 10년 + 몰수) */
	private void insertCourtJudgment() {
		long id = jdbcTemplate.queryForObject("""
				INSERT INTO judgment (case_id, subject_type, timing, penalty_type, prison_months, extra_dispositions,
				                      summary, reasoning, excerpt, plain_explanation, is_published)
				VALUES (?, 'COURT', 'FINAL', 'PRISON', 120, ?::jsonb, ?, ?, ?, ?, true)
				RETURNING id
				""", Long.class, caseId, "[{\"type\": \"CONFISCATION\", \"value\": \"범행에 사용한 흉기\"}]",
			"재판부 한 줄 요약", "재판부 판단 근거", "판결문 발췌", "쉽게 말하면");
		insertJudgmentFactor(id, weaponFactorId, "UP", "판결문 근거 문장");
		insertJudgmentFactor(id, depositFactorId, "DOWN", null);
	}

	/** 판결을 아직 등록하지 않은 사건을 만든다 (검수 누락 상황) */
	private void removePublishedJudgment(String subjectType) {
		jdbcTemplate.update("""
				DELETE FROM judgment_factor WHERE judgment_id IN
				    (SELECT id FROM judgment WHERE case_id = ? AND subject_type = ?)
				""", caseId, subjectType);
		jdbcTemplate.update("DELETE FROM judgment WHERE case_id = ? AND subject_type = ?", caseId, subjectType);
	}

	private void insertJudgmentFactor(long judgmentId, long factorId, String direction, String evidence) {
		jdbcTemplate.update(
			"INSERT INTO judgment_factor (judgment_id, factor_id, direction, evidence) VALUES (?, ?, ?, ?)",
			judgmentId, factorId, direction, evidence);
	}

	/** 그 상태의 체험 1건과 사용자 최종 판결(징역 15년, 요소 2개)을 만든다 */
	private Cookie startExperienceAt(String status) {
		insertExperience(anonymousId, caseId, 1, status);
		long myJudgmentId = jdbcTemplate.queryForObject("""
				INSERT INTO judgment (case_id, subject_type, timing, experience_id, penalty_type, prison_months, is_published)
				VALUES (?, 'USER', 'FINAL', ?, 'PRISON', 180, true)
				RETURNING id
				""", Long.class, caseId, experienceId());
		insertJudgmentFactor(myJudgmentId, weaponFactorId, "UP", null);
		insertJudgmentFactor(myJudgmentId, depositFactorId, "DOWN", null);
		return new Cookie(COOKIE, anonymousId.toString());
	}

	private String url(String path) {
		return experienceUrl(caseId) + path;
	}

	private String statusOf(long id) {
		return jdbcTemplate.queryForObject("SELECT status FROM experience WHERE id = ?", String.class, id);
	}

	private long experienceId() {
		return jdbcTemplate.queryForObject(
			"SELECT id FROM experience WHERE case_id = ? AND anonymous_user_id = ?", Long.class, caseId, anonymousId);
	}

	// --- API 10. AI 판결 ---

	@Test
	void getAiJudgment_afterVerdict_returnsAiAndMyJudgmentWithDiff() throws Exception {
		Cookie cookie = startExperienceAt("VERDICT_CONFIRMED");

		mockMvc.perform(get(url("/judgments/ai")).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.judgment.subjectType").value("AI"))
			.andExpect(jsonPath("$.judgment.prisonMonths").value(144))
			.andExpect(jsonPath("$.judgment.summary").value("AI 한 줄 요약"))
			.andExpect(jsonPath("$.judgment.reasoning").value("AI 판결 이유"))
			.andExpect(jsonPath("$.judgment.factors[0].factorId").value(weaponFactorId))
			.andExpect(jsonPath("$.myJudgment.subjectType").value("USER"))
			.andExpect(jsonPath("$.myJudgment.prisonMonths").value(180))
			.andExpect(jsonPath("$.diffFromMine.samePenaltyType").value(true))
			.andExpect(jsonPath("$.diffFromMine.prisonMonthsDiff").value(-36))
			.andExpect(jsonPath("$.references[0]").value("형법 제250조"));
	}

	@Test
	void getAiJudgment_buildsMySummaryFromFactorTags() throws Exception {
		Cookie cookie = startExperienceAt("VERDICT_CONFIRMED");

		mockMvc.perform(get(url("/judgments/ai")).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myJudgment.summary").value("흉기 사용을 무겁게 보고 피해 회복 공탁을 감안한 판단"));
	}

	@Test
	void getAiJudgment_hidesCourtOnlyFieldsAndExtraDispositions() throws Exception {
		Cookie cookie = startExperienceAt("VERDICT_CONFIRMED");

		mockMvc.perform(get(url("/judgments/ai")).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.judgment.excerpt").isEmpty())
			.andExpect(jsonPath("$.judgment.plainExplanation").isEmpty())
			.andExpect(jsonPath("$.judgment.extraDispositions").isEmpty())
			.andExpect(jsonPath("$.judgment.factors[0].evidence").isEmpty());
	}

	@Test
	void getAiJudgment_neverExposesCaseNumberOrCourtName() throws Exception {
		Cookie cookie = startExperienceAt("VERDICT_CONFIRMED");

		assertThat(bodyOf(mockMvc.perform(get(url("/judgments/ai")).cookie(cookie)).andExpect(status().isOk())))
			.doesNotContain(SECRET_CASE_NUMBER)
			.doesNotContain(SECRET_COURT_NAME);
	}

	@Test
	void getAiJudgment_beforeVerdict_returns409WithCurrentStatus() throws Exception {
		Cookie cookie = startExperienceAt("REVIEWED");

		mockMvc.perform(get(url("/judgments/ai")).cookie(cookie))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.currentStatus").value("REVIEWED"));
	}

	@Test
	void getAiJudgment_withoutCookie_returns404ExperienceNotFound() throws Exception {
		startExperienceAt("VERDICT_CONFIRMED");

		mockMvc.perform(get(url("/judgments/ai")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("EXPERIENCE_NOT_FOUND"));
	}

	@Test
	void getAiJudgment_withoutPublishedAiJudgment_returns500() throws Exception {
		Cookie cookie = startExperienceAt("VERDICT_CONFIRMED");
		removePublishedJudgment("AI");

		mockMvc.perform(get(url("/judgments/ai")).cookie(cookie))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
	}

	// --- API 11. 실제 판결 공개 ---

	@Test
	void revealCourt_firstTime_movesToAiRevealed() throws Exception {
		Cookie cookie = startExperienceAt("VERDICT_CONFIRMED");

		mockMvc.perform(post(url("/court-reveal")).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("AI_REVEALED"));

		assertThat(statusOf(experienceId())).isEqualTo("AI_REVEALED");
	}

	@Test
	void revealCourt_twice_succeedsAndKeepsStatus() throws Exception {
		Cookie cookie = startExperienceAt("VERDICT_CONFIRMED");
		mockMvc.perform(post(url("/court-reveal")).cookie(cookie)).andExpect(status().isOk());

		mockMvc.perform(post(url("/court-reveal")).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("AI_REVEALED"));

		assertThat(statusOf(experienceId())).isEqualTo("AI_REVEALED");
	}

	@Test
	void revealCourt_afterCompleted_returnsCompletedWithoutGoingBack() throws Exception {
		Cookie cookie = startExperienceAt("COMPLETED");

		mockMvc.perform(post(url("/court-reveal")).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"));

		assertThat(statusOf(experienceId())).isEqualTo("COMPLETED");
	}

	@Test
	void revealCourt_beforeVerdict_returns409AndKeepsStatus() throws Exception {
		Cookie cookie = startExperienceAt("REVIEWED");

		mockMvc.perform(post(url("/court-reveal")).cookie(cookie))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.currentStatus").value("REVIEWED"));

		assertThat(statusOf(experienceId())).isEqualTo("REVIEWED");
	}

	@Test
	void revealCourt_withoutPublishedCourtJudgment_keepsStatusSoUserIsNotTrapped() throws Exception {
		// 상태만 넘어가면 그 뒤 API 12가 매번 500이라 되돌릴 방법 없이 결과 화면에 갇힌다
		Cookie cookie = startExperienceAt("VERDICT_CONFIRMED");
		removePublishedJudgment("COURT");

		mockMvc.perform(post(url("/court-reveal")).cookie(cookie))
			.andExpect(status().isInternalServerError());

		assertThat(statusOf(experienceId())).isEqualTo("VERDICT_CONFIRMED");
	}

	// --- API 12. 실제 판결 ---

	@Test
	void getCourtJudgment_afterReveal_returnsThreeJudgmentsWithSource() throws Exception {
		Cookie cookie = startExperienceAt("AI_REVEALED");

		mockMvc.perform(get(url("/judgments/court")).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.judgment.subjectType").value("COURT"))
			.andExpect(jsonPath("$.judgment.prisonMonths").value(120))
			.andExpect(jsonPath("$.judgment.excerpt").value("판결문 발췌"))
			.andExpect(jsonPath("$.judgment.plainExplanation").value("쉽게 말하면"))
			.andExpect(jsonPath("$.judgment.extraDispositions[0].type").value("CONFISCATION"))
			.andExpect(jsonPath("$.judgment.factors[0].evidence").value("판결문 근거 문장"))
			.andExpect(jsonPath("$.myJudgment.prisonMonths").value(180))
			.andExpect(jsonPath("$.aiJudgment.prisonMonths").value(144))
			.andExpect(jsonPath("$.source.sourceOrg").value(SOURCE_ORG))
			.andExpect(jsonPath("$.deidentifiedItems[0]").value("인명"));
	}

	@Test
	void getCourtJudgment_neverExposesCaseNumberOrCourtName() throws Exception {
		Cookie cookie = startExperienceAt("AI_REVEALED");

		assertThat(bodyOf(mockMvc.perform(get(url("/judgments/court")).cookie(cookie)).andExpect(status().isOk())))
			.doesNotContain(SECRET_CASE_NUMBER)
			.doesNotContain(SECRET_COURT_NAME);
	}

	@Test
	void getCourtJudgment_beforeReveal_returns409WithCurrentStatus() throws Exception {
		Cookie cookie = startExperienceAt("VERDICT_CONFIRMED");

		mockMvc.perform(get(url("/judgments/court")).cookie(cookie))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.currentStatus").value("VERDICT_CONFIRMED"));
	}

	@Test
	void getCourtJudgment_doesNotChangeStatus() throws Exception {
		Cookie cookie = startExperienceAt("AI_REVEALED");

		mockMvc.perform(get(url("/judgments/court")).cookie(cookie)).andExpect(status().isOk());

		assertThat(statusOf(experienceId())).isEqualTo("AI_REVEALED");
	}

	@Test
	void getCourtJudgment_withoutPublishedCourtJudgment_returns500() throws Exception {
		Cookie cookie = startExperienceAt("AI_REVEALED");
		removePublishedJudgment("COURT");

		mockMvc.perform(get(url("/judgments/court")).cookie(cookie))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
	}

	// --- API 13. 비교 공개 ---

	@Test
	void revealComparison_firstTime_movesToCompleted() throws Exception {
		Cookie cookie = startExperienceAt("AI_REVEALED");

		mockMvc.perform(post(url("/comparison-reveal")).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"));

		assertThat(statusOf(experienceId())).isEqualTo("COMPLETED");
	}

	@Test
	void revealComparison_twice_succeedsAndKeepsStatus() throws Exception {
		Cookie cookie = startExperienceAt("AI_REVEALED");
		mockMvc.perform(post(url("/comparison-reveal")).cookie(cookie)).andExpect(status().isOk());

		mockMvc.perform(post(url("/comparison-reveal")).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"));

		assertThat(statusOf(experienceId())).isEqualTo("COMPLETED");
	}

	@Test
	void revealComparison_withoutPublishedJudgment_keepsStatusSoUserIsNotTrapped() throws Exception {
		// API 11과 같은 이유 — 상태만 COMPLETED로 넘어가면 그 뒤 API 14가 매번 500이라 갇힌다
		Cookie cookie = startExperienceAt("AI_REVEALED");
		removePublishedJudgment("AI");

		mockMvc.perform(post(url("/comparison-reveal")).cookie(cookie))
			.andExpect(status().isInternalServerError());

		assertThat(statusOf(experienceId())).isEqualTo("AI_REVEALED");
	}

	@Test
	void revealComparison_beforeCourtReveal_returns409AndKeepsStatus() throws Exception {
		Cookie cookie = startExperienceAt("VERDICT_CONFIRMED");

		mockMvc.perform(post(url("/comparison-reveal")).cookie(cookie))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.currentStatus").value("VERDICT_CONFIRMED"));

		assertThat(statusOf(experienceId())).isEqualTo("VERDICT_CONFIRMED");
	}

	private static String bodyOf(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}
}
