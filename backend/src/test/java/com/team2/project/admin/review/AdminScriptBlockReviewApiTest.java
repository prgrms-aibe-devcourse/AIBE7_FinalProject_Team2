package com.team2.project.admin.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 대본 블록 단위 검수 A-13-18 ~ 21과 낡음 표시 (BE-126 2차). 블록 근거(basis)는 적재가 남기는 모양 그대로 넣는다 — 지금 값과
 * 같게 시작하고, 사건 값 · 앞 블록을 바꿔 낡음을 만든다.
 */
class AdminScriptBlockReviewApiTest extends ApiIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";
	private static final String CSRF_COOKIE = "XSRF-TOKEN";
	private static final String CAST = "{\"prosecutor\": \"naero\", \"defense\": \"nambul\"}";
	private static final String AXES = "\"mood\": \"tragic\", \"issue\": \"admitted\", \"victim\": \"punish\", \"thirdParty\": false";

	@Autowired AdminAccountRepository accountRepository;
	@Autowired PasswordEncoder passwordEncoder;

	private AdminAccount admin;
	private MockHttpSession session;
	private Cookie csrf;
	private long caseId;
	private long scriptId;

	@BeforeEach
	void setUp() throws Exception {
		admin = accountRepository.save(AdminAccount.withPassword("block-" + UUID.randomUUID() + "@example.com",
			passwordEncoder.encode(PASSWORD), "블록 검수 테스트"));
		login();
		caseId = insertCase("MURDER", "DRAFT");
		jdbcTemplate.update("UPDATE legal_case SET classification = ?::jsonb, sensitive_flags = '[]'::jsonb, cast_roles = ?::jsonb "
			+ "WHERE id = ?", "{" + AXES + "}", CAST, caseId);
		insertCasePaper(caseId, "기사 제목");
		scriptId = jdbcTemplate.queryForObject("""
			INSERT INTO case_script (case_id, model, generated_at, stopped, cast_roles, issue, admits, publishable)
			VALUES (?, 'openai:test', now(), false, ?::jsonb, 'admitted', true, true) RETURNING id""", Long.class, caseId, CAST);
		insertBlock("U0", """
			{"plea": "인정", "issues": ["경위"], "questions": [{"q": "첫 질문입니까?", "a": "예"},
			 {"q": "둘째 질문입니까?", "a": "예"}, {"q": "공소사실을 인정합니까?", "a": "예"}]}""",
			"{\"case\": 1, " + AXES + ", \"sensitiveFlags\": []}",
			"[{\"unit\": \"U0\", \"path\": \"/questions/0/q\", \"code\": \"X\", \"severity\": \"error\"}]");
		insertBlock("U1", "{\"lines\": [\"해설\"]}", "{\"mood\": \"tragic\", \"sensitiveFlags\": [], \"paper\": 1}", "[]");
		insertBlock("U2", "{\"questions\": [{\"q\": \"질문\", \"a\": \"예\"}]}",
			"{\"case\": 1, \"cast\": " + CAST + ", \"mood\": \"tragic\", \"issue\": \"admitted\", \"thirdParty\": false, "
				+ "\"sensitiveFlags\": [], \"U0\": 1}", "[]");
		insertBlock("U3", "[{\"factorId\": 1, \"role\": \"prosecutor\", \"text\": \"대사입니다.\"}]",
			"{\"case\": 1, \"cast\": " + CAST + ", \"victim\": \"punish\", \"thirdParty\": false, \"sensitiveFlags\": []}", "[]");
		insertBlock("U4", "{\"prosecutor\": \"의견\", \"defense\": \"변론\"}",
			"{\"case\": 1, \"cast\": " + CAST + ", \"mood\": \"tragic\", \"issue\": \"admitted\", \"victim\": \"punish\", "
				+ "\"sensitiveFlags\": [], \"U0\": 1, \"U2\": 1}", "[]");
	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM penalty_rule WHERE case_id = ?", caseId);
		jdbcTemplate.update("DELETE FROM admin_audit_log WHERE actor_email = ?", admin.getEmail());
		accountRepository.deleteById(admin.getId());
	}

	@Test
	@DisplayName("낡음: 근거가 지금 값과 같으면 낡지 않았다. 배역을 바꾸면 배역이 말하는 U2 · U3 · U4만 낡음 — 표시만 하고 승인은 막지 않는다")
	void staleness_castChange_isDisplayOnly() throws Exception {
		perform(get(scriptUrl()))
			.andExpect(jsonPath("$.blocks[*].stale", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))));

		jdbcTemplate.update("UPDATE legal_case SET cast_roles = '{\"prosecutor\": \"nambul\", \"defense\": \"naero\"}'::jsonb "
			+ "WHERE id = ?", caseId);
		perform(get(scriptUrl()))
			.andExpect(jsonPath("$.blocks[0].stale").value(false))
			.andExpect(jsonPath("$.blocks[1].stale").value(false))
			.andExpect(jsonPath("$.blocks[2].staleReasons[0]").value("cast"))
			.andExpect(jsonPath("$.blocks[3].staleReasons[0]").value("cast"))
			.andExpect(jsonPath("$.blocks[4].staleBlocksApproval").value(false));
		perform(post(scriptUrl() + "/blocks/U2/approve")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("낡음: 민감 플래그가 빠지면 모든 블록이 승인을 막는 낡음 — 확인했음(A-13-20)으로 근거를 지금 값에 맞추면 그 블록만 풀린다")
	void staleness_removedSensitiveFlag_blocksApprovalUntilAcknowledged() throws Exception {
		jdbcTemplate.update("UPDATE case_script_block SET basis = jsonb_set(basis, '{sensitiveFlags}', '[\"SELF_HARM\"]') "
			+ "WHERE script_id = ?", scriptId);	// 플래그를 가진 채 만든 대본
		perform(get(scriptUrl()))
			.andExpect(jsonPath("$.blocks[1].staleReasons[0]").value("sensitiveFlags"))
			.andExpect(jsonPath("$.blocks[1].staleBlocksApproval").value(true));
		perform(post(scriptUrl() + "/blocks/U1/approve"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message", containsString("U1")));

		perform(post(scriptUrl() + "/blocks/U1/acknowledge-stale"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.blocks[1].stale").value(false))
			.andExpect(jsonPath("$.blocks[1].basis.sensitiveFlags.length()").value(0))
			.andExpect(jsonPath("$.blocks[2].staleBlocksApproval").value(true));	// 확인한 블록만 풀린다
		perform(post(scriptUrl() + "/blocks/U1/approve")).andExpect(status().isOk());
		assertThat(auditDetails()).contains("acknowledgedStale").contains("sensitiveFlags");
	}

	@Test
	@DisplayName("A-13-20: 민감 플래그가 새로 생긴 블록은 확인했음으로 둘 수 없다 — 그 플래그의 가드 없이 만들어졌다(다시 생성해야 한다)")
	void acknowledge_addedSensitiveFlag_isConflict() throws Exception {
		jdbcTemplate.update("UPDATE legal_case SET sensitive_flags = '[\"SELF_HARM\"]'::jsonb WHERE id = ?", caseId);
		perform(post(scriptUrl() + "/blocks/U1/acknowledge-stale"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message", containsString("SELF_HARM")));
		perform(get(scriptUrl())).andExpect(jsonPath("$.blocks[1].staleBlocksApproval").value(true));
	}

	@Test
	@DisplayName("점검 (2차-2): 공개 사건의 대본을 승인하면 나간 시각을 남기고 READY, 블록 반려 · 문장 · 대사 수정은 막지 않고 점검 중(API 18 503), 다시 모두 승인하면 돌아온다")
	void publishedCase_blockChangesPutScriptUnderMaintenance() throws Exception {
		jdbcTemplate.update("UPDATE case_script_block SET review = '[]'::jsonb, publishable = true WHERE script_id = ?", scriptId);
		jdbcTemplate.update("UPDATE legal_case SET status = 'PUBLISHED', published_at = now() WHERE id = ?", caseId);
		perform(get(scriptUrl())).andExpect(jsonPath("$.scriptStatus").value("PREPARING"))
			.andExpect(jsonPath("$.releasedAt").value((Object) null));
		mockMvc.perform(get(publicScriptUrl())).andExpect(status().isNotFound());

		perform(post(scriptUrl() + "/approve"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scriptStatus").value("READY"))
			.andExpect(jsonPath("$.releasedAt").isNotEmpty());
		String releasedAt = releasedAt();
		mockMvc.perform(get(publicScriptUrl())).andExpect(status().isOk());

		perform(post(scriptUrl() + "/blocks/U2/reject").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"어색합니다\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scriptStatus").value("MAINTENANCE"));
		mockMvc.perform(get(publicScriptUrl()))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("SCRIPT_UNDER_MAINTENANCE"));
		mockMvc.perform(get("/api/v1/cases/" + caseId))
			.andExpect(jsonPath("$.scriptStatus").value("MAINTENANCE"))
			.andExpect(jsonPath("$.scriptReady").value(false));
		// 점검 중에도 계속 고칠 수 있다 — 점검을 만든 요청만 감사 로그에 scriptMaintenance가 남는다
		perform(patch(scriptUrl() + "/blocks/U0/text").contentType(MediaType.APPLICATION_JSON)
				.content("{\"path\": \"/questions/0/q\", \"text\": \"고친 질문입니까?\"}"))
			.andExpect(status().isOk());
		perform(patch(scriptUrl() + "/evidence/1").contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\": \"고친 대사입니다.\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scriptStatus").value("MAINTENANCE"));
		assertThat(jdbcTemplate.queryForList("""
			SELECT action FROM admin_audit_log WHERE actor_email = ? AND detail ->> 'scriptMaintenance' = 'true'""",
			String.class, admin.getEmail())).containsExactly("CASE_SCRIPT_REJECT");

		for (String unit : List.of("U0", "U2", "U3")) {		// U0 판이 올라 U2 · U4가 낡았지만 표시만 하는 낡음이다
			perform(post(scriptUrl() + "/blocks/" + unit + "/approve")).andExpect(status().isOk());
		}
		perform(get(scriptUrl())).andExpect(jsonPath("$.scriptStatus").value("READY"));
		mockMvc.perform(get(publicScriptUrl())).andExpect(status().isOk());
		assertThat(releasedAt()).isEqualTo(releasedAt);		// 처음 나간 시각은 그대로다
	}

	@Test
	@DisplayName("점검 (2차-2): READY 대본에서 바로 문장 수정(A-13-21) · 대사 수정(A-13-13)해도 점검 중이 되고, 그 요청에 scriptMaintenance가 남는다")
	void publishedCase_editsFromReady_putScriptUnderMaintenance() throws Exception {
		jdbcTemplate.update("UPDATE case_script_block SET review = '[]'::jsonb, publishable = true WHERE script_id = ?", scriptId);
		jdbcTemplate.update("UPDATE legal_case SET status = 'PUBLISHED', published_at = now() WHERE id = ?", caseId);
		perform(post(scriptUrl() + "/approve")).andExpect(jsonPath("$.scriptStatus").value("READY"));

		perform(patch(scriptUrl() + "/blocks/U0/text").contentType(MediaType.APPLICATION_JSON)
				.content("{\"path\": \"/questions/0/q\", \"text\": \"고친 질문입니까?\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scriptStatus").value("MAINTENANCE"));
		mockMvc.perform(get(publicScriptUrl())).andExpect(status().isServiceUnavailable());
		assertThat(maintenanceAuditDetails()).hasSize(1).first().asString().contains("\"unit\": \"U0\"");

		perform(post(scriptUrl() + "/blocks/U0/approve")).andExpect(jsonPath("$.scriptStatus").value("READY"));
		perform(patch(scriptUrl() + "/evidence/1").contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\": \"고친 대사입니다.\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scriptStatus").value("MAINTENANCE"));
		mockMvc.perform(get(publicScriptUrl())).andExpect(status().isServiceUnavailable());
		assertThat(maintenanceAuditDetails()).hasSize(2).last().asString().contains("\"factorId\": 1");
	}

	@Test
	@DisplayName("점검 (2차-2): 초안 사건에서 승인만 한 대본은 나간 것이 아니다 — 고치면 준비 중, 승인한 채 사건을 공개(A-13)하면 그때 나간 시각을 남긴다")
	void draftCase_approvalIsNotRelease() throws Exception {
		jdbcTemplate.update("UPDATE case_script_block SET review = '[]'::jsonb, publishable = true WHERE script_id = ?", scriptId);
		perform(post(scriptUrl() + "/approve")).andExpect(status().isOk())
			.andExpect(jsonPath("$.releasedAt").value((Object) null));
		perform(post(scriptUrl() + "/blocks/U4/reject").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"고칩니다\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scriptStatus").value("PREPARING"));
		perform(post(scriptUrl() + "/blocks/U4/approve")).andExpect(status().isOk());

		insertPublishPrerequisites();
		perform(post("/api/v1/admin/cases/" + caseId + "/publish")).andExpect(status().isOk())
			.andExpect(jsonPath("$.script.scriptStatus").value("READY"));
		assertThat(releasedAt()).isNotNull();
		perform(get(scriptUrl())).andExpect(jsonPath("$.releasedAt").isNotEmpty());
	}

	@Test
	@DisplayName("낡음: 근거에 없는 키는 비교하지 않고 모름(unknownBasis)으로 둔다 — 이관 · 증거조사만 만든 대본")
	void staleness_missingBasisKey_isUnknown() throws Exception {
		jdbcTemplate.update("UPDATE case_script_block SET basis = '{\"case\": 1}'::jsonb WHERE script_id = ? AND unit = 'U3'",
			scriptId);
		jdbcTemplate.update("UPDATE legal_case SET cast_roles = '{\"prosecutor\": \"nambul\", \"defense\": \"naero\"}'::jsonb "
			+ "WHERE id = ?", caseId);
		perform(get(scriptUrl()))
			.andExpect(jsonPath("$.blocks[3].stale").value(false))
			.andExpect(jsonPath("$.blocks[3].unknownBasis", org.hamcrest.Matchers.hasItems("cast", "victim", "sensitiveFlags")));
	}

	@Test
	@DisplayName("A-13-18 · 19: 블록마다 승인 · 반려한다 — 다섯 블록이 모두 승인되면 공개, 오류 블록은 승인할 수 없다")
	void approveAndRejectBlocks() throws Exception {
		perform(post(scriptUrl() + "/blocks/U0/approve"))		// U0에 검수 오류가 있다
			.andExpect(status().isConflict());
		jdbcTemplate.update("UPDATE case_script_block SET review = '[]'::jsonb, publishable = true WHERE script_id = ? AND unit = 'U0'",
			scriptId);
		for (String unit : List.of("U0", "U1", "U2", "U3")) {
			perform(post(scriptUrl() + "/blocks/" + unit + "/approve")).andExpect(status().isOk())
				.andExpect(jsonPath("$.isPublished").value(false));
		}
		perform(post(scriptUrl() + "/blocks/U4/approve"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isPublished").value(true))
			.andExpect(jsonPath("$.reviewStatus").value("APPROVED"));

		perform(post(scriptUrl() + "/blocks/U2/reject").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \"질문이 어색합니다\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isPublished").value(false))
			.andExpect(jsonPath("$.reviewStatus").value("REJECTED"))
			.andExpect(jsonPath("$.blocks[2].reviewNote").value("질문이 어색합니다"))
			.andExpect(jsonPath("$.blocks[0].reviewStatus").value("APPROVED"));
		assertThat(auditDetails()).contains("\"unit\": \"U4\"");
	}

	@Test
	@DisplayName("A-13-18: 통승인과 같은 대본 조건 — 기사(PAPER)가 pending이거나 생성이 멈춘 대본은 블록 하나씩도 승인할 수 없다")
	void approveBlock_scriptConditions() throws Exception {
		jdbcTemplate.update("UPDATE case_script_block SET review = '[]'::jsonb, publishable = true WHERE script_id = ?", scriptId);
		jdbcTemplate.update("UPDATE case_script SET pending = '[\"PAPER\"]'::jsonb WHERE id = ?", scriptId);
		perform(post(scriptUrl() + "/blocks/U1/approve"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message", containsString("만들지 못한")));
		jdbcTemplate.update("UPDATE case_script SET pending = '[]'::jsonb, stopped = true WHERE id = ?", scriptId);
		perform(post(scriptUrl() + "/blocks/U1/approve")).andExpect(status().isConflict());
		jdbcTemplate.update("UPDATE case_script SET stopped = false WHERE id = ?", scriptId);
		perform(post(scriptUrl() + "/blocks/U1/approve")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("A-13-18: 모르는 단위는 400, 없는 블록은 404 SCRIPT_BLOCK_NOT_FOUND")
	void unknownUnitOrMissingBlock() throws Exception {
		perform(post(scriptUrl() + "/blocks/U9/approve")).andExpect(status().isBadRequest());
		jdbcTemplate.update("DELETE FROM case_script_block WHERE script_id = ? AND unit = 'U4'", scriptId);
		perform(post(scriptUrl() + "/blocks/U4/approve"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SCRIPT_BLOCK_NOT_FOUND"));
	}

	@Test
	@DisplayName("A-13-21: U0 줄을 고치면 판이 오르고 그 블록만 대기, 그 자리 오류가 지워지고, 혐의 인정 여부를 다시 계산한다 — U2 · U4는 낡음")
	void editText_u0() throws Exception {
		jdbcTemplate.update("UPDATE case_script_block SET review_status = 'APPROVED', reviewed_at = now() WHERE script_id = ? "
			+ "AND unit <> 'U0'", scriptId);
		perform(patch(scriptUrl() + "/blocks/U0/text").contentType(MediaType.APPLICATION_JSON)
				.content("{\"path\": \"/questions/2/a\", \"text\": \"아니요\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.meta.admits").value(false))
			.andExpect(jsonPath("$.blocks[0].revision").value(2))
			.andExpect(jsonPath("$.blocks[0].content.questions[2].a").value("아니요"))
			.andExpect(jsonPath("$.blocks[0].review.length()").value(1))		// 다른 자리의 오류는 남는다
			.andExpect(jsonPath("$.blocks[2].staleReasons[0]").value("U0"))
			.andExpect(jsonPath("$.blocks[4].staleReasons[0]").value("U0"))
			.andExpect(jsonPath("$.blocks[2].reviewStatus").value("APPROVED"));	// 뒤 블록 승인은 그대로(표시만)

		perform(patch(scriptUrl() + "/blocks/U0/text").contentType(MediaType.APPLICATION_JSON)
				.content("{\"path\": \"/questions/0/q\", \"text\": \"고친 질문입니까?\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.blocks[0].review.length()").value(0))
			.andExpect(jsonPath("$.blocks[0].publishable").value(true));
		assertThat(auditDetails()).contains("/questions/0/q").doesNotContain("고친 질문");
	}

	@Test
	@DisplayName("A-13-21: 없는 자리 · 문자열이 아닌 자리는 404, U3는 400(A-13-13으로), 길이 규칙 위반은 400")
	void editText_rejects() throws Exception {
		for (String path : List.of("/questions/9/q", "/questions", "/nothing", "questions/0/q")) {
			perform(patch(scriptUrl() + "/blocks/U0/text").contentType(MediaType.APPLICATION_JSON)
					.content("{\"path\": \"" + path + "\", \"text\": \"문장\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SCRIPT_LINE_NOT_FOUND"));
		}
		perform(patch(scriptUrl() + "/blocks/U3/text").contentType(MediaType.APPLICATION_JSON)
				.content("{\"path\": \"/0/text\", \"text\": \"문장\"}"))
			.andExpect(status().isBadRequest());
		perform(patch(scriptUrl() + "/blocks/U1/text").contentType(MediaType.APPLICATION_JSON)
				.content("{\"path\": \"/lines/0\", \"text\": \"" + "가".repeat(101) + "\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("A-13-21: 대사 길이(100자 · 2줄)는 말하는 대사 자리에만 — 설계도(U0) 질문은 101자 · 세 줄도 받고 500자를 넘으면 400 (BE-153)")
	void editText_lengthRuleOnlyOnSpokenPaths() throws Exception {
		String longQuestion = "가".repeat(60) + "\\n" + "나".repeat(41) + "\\n" + "다";	// 102자 · 세 줄
		perform(patch(scriptUrl() + "/blocks/U0/text").contentType(MediaType.APPLICATION_JSON)
				.content("{\"path\": \"/questions/0/q\", \"text\": \"" + longQuestion + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.blocks[0].content.questions[0].q").value(longQuestion.replace("\\n", "\n")));
		perform(patch(scriptUrl() + "/blocks/U0/text").contentType(MediaType.APPLICATION_JSON)
				.content("{\"path\": \"/questions/0/q\", \"text\": \"" + "가".repeat(501) + "\"}"))
			.andExpect(status().isBadRequest());
		perform(patch(scriptUrl() + "/blocks/U2/text").contentType(MediaType.APPLICATION_JSON)
				.content("{\"path\": \"/questions/0/q\", \"text\": \"" + "가".repeat(101) + "\"}"))
			.andExpect(status().isBadRequest());		// U2 신문 질문은 말하는 대사다
	}

	@Test
	@DisplayName("A-11: 민감 플래그를 고치면 승인한 블록의 승인이 지워진다 — 승인을 막는 낡음이라서(감사 로그에 단위)")
	void editCase_sensitiveFlags_revokesApprovals() throws Exception {
		jdbcTemplate.update("UPDATE case_script_block SET review = '[]'::jsonb, publishable = true, review_status = 'APPROVED', "
			+ "reviewed_at = now() WHERE script_id = ?", scriptId);
		perform(patch("/api/v1/admin/cases/" + caseId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"sensitiveFlags\": [\"MENTAL_HEALTH\"]}"))
			.andExpect(status().isOk());
		assertThat(jdbcTemplate.queryForList("SELECT review_status FROM case_script_block WHERE script_id = ?", String.class,
			scriptId)).containsOnlyNulls();
		assertThat(auditDetails()).contains("scriptApprovalRevoked");
	}

	// --- 도움 함수 ---

	private void insertBlock(String unit, String content, String basis, String review) {
		jdbcTemplate.update("""
			INSERT INTO case_script_block (script_id, unit, content, revision, publishable, basis, review)
			VALUES (?, ?, ?::jsonb, 1, ?, ?::jsonb, ?::jsonb)""", scriptId, unit, content, "[]".equals(review), basis, review);
	}

	private String scriptUrl() {
		return "/api/v1/admin/cases/" + caseId + "/script";
	}

	/** API 18 (사용자 쪽 공개 대본) */
	private String publicScriptUrl() {
		return "/api/v1/cases/" + caseId + "/script";
	}

	/** 대본을 점검 중으로 만든 요청의 감사 로그 detail (오래된 순) */
	private List<String> maintenanceAuditDetails() {
		return jdbcTemplate.queryForList("""
			SELECT detail::text FROM admin_audit_log WHERE actor_email = ? AND detail ->> 'scriptMaintenance' = 'true' ORDER BY id""",
			String.class, admin.getEmail());
	}

	private String releasedAt() {
		return jdbcTemplate.queryForObject("SELECT released_at::text FROM case_script WHERE id = ?", String.class, scriptId);
	}

	/** 사건 공개(A-13) 조건 — 판단 요소(개요 단계 포함), 공개한 AI · 실제 판결, 형벌 규칙, 신문 지면(setUp에서 넣었다) */
	private void insertPublishPrerequisites() {
		insertFactor(caseId, "OVERVIEW", "흉기를 집어 들었다", null, 1);
		jdbcTemplate.update("""
			INSERT INTO judgment (case_id, subject_type, timing, penalty_type, prison_months, is_published)
			VALUES (?, 'AI', 'FINAL', 'PRISON', 144, true), (?, 'COURT', 'FINAL', 'PRISON', 144, true)""", caseId, caseId);
		jdbcTemplate.update("""
			INSERT INTO penalty_rule (case_id, penalty_type, allowed_min, allowed_max, suspension_allowed, display_order)
			VALUES (?, 'PRISON', 60, 360, true, 1)""", caseId);
	}

	private String auditDetails() {
		return String.join(" ", jdbcTemplate.queryForList(
			"SELECT coalesce(detail::text, '') FROM admin_audit_log WHERE actor_email = ? ORDER BY id", String.class,
			admin.getEmail()));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.session(session).cookie(csrf).header(CSRF_HEADER, csrf.getValue()));
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
