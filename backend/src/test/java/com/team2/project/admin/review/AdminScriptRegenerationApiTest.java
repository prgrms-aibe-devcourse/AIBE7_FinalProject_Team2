package com.team2.project.admin.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.pipeline.PipelineClient;
import com.team2.project.pipeline.PipelineException;
import com.team2.project.pipeline.PipelineJob;
import com.team2.project.pipeline.PipelineRegenerationRequest;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 대본 블록 재생성 A-13-15 ~ 17 (BE-129 2차)와 A-11 분류 수정. 파이프라인 클라이언트는 가짜다(TestPipelineClientConfig) —
 * 보내는 입력 · 받은 결과의 반영을 본다. 실제 재생성은 파이프라인 테스트(`tools/ai-judgment/tests/test_script_regenerate.py`)가 본다.
 */
class AdminScriptRegenerationApiTest extends ApiIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";
	private static final String CSRF_COOKIE = "XSRF-TOKEN";
	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final String CLASSIFICATION =
		"{\"mood\": \"tragic\", \"issue\": \"admitted\", \"victim\": \"punish\", \"thirdParty\": false}";
	private static final String CAST = "{\"prosecutor\": \"naero\", \"defense\": \"nambul\"}";
	private static final String NEW_CLOSING = "{\"prosecutor\": \"검사 측 의견입니다. 새 의견.\", \"defense\": \"변호인 측 최종변론입니다. 새 변론.\"}";

	@Autowired AdminAccountRepository accountRepository;
	@Autowired PasswordEncoder passwordEncoder;
	@Autowired PipelineClient pipelineClient;

	private AdminAccount admin;
	private MockHttpSession session;
	private Cookie csrf;
	private long caseId;
	private long scriptId;

	@BeforeEach
	void setUp() throws Exception {
		Mockito.reset(pipelineClient);	// 가짜를 여러 테스트가 나눠 쓴다
		when(pipelineClient.enabled()).thenReturn(true);
		when(pipelineClient.createRegenerationJob(any())).thenAnswer(invocation -> job("QUEUED"));
		admin = accountRepository.save(AdminAccount.withPassword("regen-" + UUID.randomUUID() + "@example.com",
			passwordEncoder.encode(PASSWORD), "재생성 테스트"));
		login();
		caseId = insertCase("MURDER", "DRAFT");
		jdbcTemplate.update("""
			UPDATE legal_case SET classification = ?::jsonb, sensitive_flags = '["SELF_HARM"]'::jsonb, cast_roles = ?::jsonb,
			       recommended_min_months = 60, recommended_max_months = 120,
			       classification_needs_review = '["mood", "sensitiveFlags.SELF_HARM"]'::jsonb
			WHERE id = ?""", CLASSIFICATION, CAST, caseId);
		long first = insertFactor(caseId, "OVERVIEW", "흉기를 집어 들었다", "흉기", 1);
		long second = insertFactor(caseId, "DETAIL", "구호 조치 없이 떠났다", null, 2);
		jdbcTemplate.update("""
			INSERT INTO case_section (case_id, stage, section_type, title, content, display_order) VALUES
			(?, 'DETAIL', 'FACTS', '주요 사실', '말다툼 끝에 흉기를 들었다.', 1),
			(?, 'LAW', 'LAW_TERM', '용어', '용어 설명', 1)""", caseId, caseId);
		long judgmentId = jdbcTemplate.queryForObject("""
			INSERT INTO judgment (case_id, subject_type, timing, penalty_type, prison_months, summary, reasoning, is_published)
			VALUES (?, 'AI', 'FINAL', 'PRISON', 96, '요약', '이유', false) RETURNING id""", Long.class, caseId);
		jdbcTemplate.update("INSERT INTO judgment_factor (judgment_id, factor_id, direction) VALUES (?, ?, 'UP'), (?, ?, 'DOWN')",
			judgmentId, first, judgmentId, second);
		insertCasePaper(caseId, "기사 제목");
		scriptId = jdbcTemplate.queryForObject("""
			INSERT INTO case_script (case_id, model, generated_at, stopped, cast_roles, issue, admits, publishable)
			VALUES (?, 'openai:old', now(), false, ?::jsonb, 'admitted', true, true) RETURNING id""", Long.class, caseId, CAST);
		insertBlock("U0", "{\"questions\": [{\"q\": \"a\", \"a\": \"예\"}, {\"q\": \"b\", \"a\": \"예\"}, {\"q\": \"c\", \"a\": \"예\"}]}", 2);
		insertBlock("U1", "{\"lines\": [\"해설\"]}", 1);
		insertBlock("U2", "{\"questions\": []}", 1);
		insertBlock("U3", """
			[{"factorId": 1, "role": "prosecutor", "text": "사람이 고친 줄입니다.", "validation": {"ok": true, "manual": true}},
			 {"factorId": 2, "role": "defense", "text": "모델이 쓴 줄입니다.", "validation": {"ok": true}}]""", 3);
		insertBlock("U4", "{\"prosecutor\": \"옛 의견\", \"defense\": \"옛 변론\"}", 1);
	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM case_section WHERE case_id = ?", caseId);
		jdbcTemplate.update("DELETE FROM admin_audit_log WHERE actor_email = ?", admin.getEmail());
		accountRepository.deleteById(admin.getId());
	}

	@Test
	@DisplayName("A-13-15: DB 값으로 입력을 모아 보낸다 — 분류에 민감 플래그를 합치고, 판결 방향 · 형량, 지금 블록 판, 기사를 넣는다")
	void start_sendsDbInputs() throws Exception {
		perform(post(regenerationsUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"units\": [\"U4\"]}"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("QUEUED"))
			.andExpect(jsonPath("$.units[0]").value("U4"))
			.andExpect(jsonPath("$.cascade").value(true))
			.andExpect(jsonPath("$.applicable").value(false));

		ArgumentCaptor<PipelineRegenerationRequest> captor = ArgumentCaptor.forClass(PipelineRegenerationRequest.class);
		verify(pipelineClient).createRegenerationJob(captor.capture());
		JsonNode sent = MAPPER.valueToTree(captor.getValue());
		JsonNode regenerate = sent.get("regenerate");
		assertThat(sent.at("/stages/script/model").asString()).isEqualTo("openai:test-script");
		assertThat(regenerate.at("/classification/sensitiveFlags/0").asString()).isEqualTo("SELF_HARM");	// 합쳐 넘긴다
		assertThat(regenerate.at("/classification/mood").asString()).isEqualTo("tragic");
		assertThat(regenerate.at("/cast/defense").asString()).isEqualTo("nambul");
		assertThat(regenerate.at("/judgment/prisonMonths").asInt()).isEqualTo(96);
		assertThat(regenerate.at("/judgment/factors/1/direction").asString()).isEqualTo("DOWN");
		assertThat(regenerate.at("/judgment/factors/1/factorId").asInt()).isEqualTo(2);	// display_order
		assertThat(regenerate.at("/case/recommended/maxMonths").asInt()).isEqualTo(120);
		assertThat(regenerate.at("/case/factors/0/label").asString()).isEqualTo("흉기를 집어 들었다");
		assertThat(regenerate.at("/case/sections").size()).isEqualTo(1);	// 용어 설명(LAW_TERM)은 뺀다
		assertThat(regenerate.at("/blocks/U3/revision").asInt()).isEqualTo(3);
		assertThat(regenerate.at("/paper/headline").asString()).isEqualTo("기사 제목");
		assertThat(regenerate.toString()).doesNotContain("caseNumber");	// 원본 정보는 보내지 않는다
		assertThat(jdbcTemplate.queryForObject("SELECT inputs ->> 'case' FROM case_script_regeneration WHERE case_id = ?",
			String.class, caseId)).isEqualTo("1");
		assertThat(auditActions()).containsExactly("CASE_SCRIPT_REGENERATE");
	}

	@Test
	@DisplayName("A-13-15: 미분류(민감 플래그 null) · 배역 없음 · 1막 해설이 있는데 기사 없음 · AI 판결 없음은 409로 막고 작업을 만들지 않는다")
	void start_guards() throws Exception {
		jdbcTemplate.update("UPDATE legal_case SET sensitive_flags = NULL WHERE id = ?", caseId);
		expectConflict("민감 플래그");
		jdbcTemplate.update("UPDATE legal_case SET sensitive_flags = '[]'::jsonb, cast_roles = NULL WHERE id = ?", caseId);
		expectConflict("배역");
		jdbcTemplate.update("UPDATE legal_case SET cast_roles = ?::jsonb WHERE id = ?", CAST, caseId);
		jdbcTemplate.update("DELETE FROM case_paper WHERE case_id = ?", caseId);
		expectConflict("신문 기사");
		insertCasePaper(caseId, "기사 제목");
		jdbcTemplate.update("DELETE FROM judgment_factor WHERE judgment_id IN (SELECT id FROM judgment WHERE case_id = ?)", caseId);
		jdbcTemplate.update("DELETE FROM judgment WHERE case_id = ?", caseId);
		expectConflict("AI 판결");
		verify(pipelineClient, never()).createRegenerationJob(any());
	}

	@Test
	@DisplayName("A-13-15: 모르는 단위 · 겹친 단위 · 빈 목록은 400, 파이프라인이 다른 작업을 돌리면 409 IMPORT_IN_PROGRESS")
	void start_badRequests() throws Exception {
		for (String body : List.of("{\"units\": []}", "{\"units\": [\"U9\"]}", "{\"units\": [\"U4\", \"U4\"]}")) {
			perform(post(regenerationsUrl()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		}
		when(pipelineClient.createRegenerationJob(any())).thenThrow(new PipelineException.Conflict("busy"));
		perform(post(regenerationsUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"units\": [\"U4\"]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IMPORT_IN_PROGRESS"));
	}

	@Test
	@DisplayName("A-13-16: 끝나면 미리보기를 준다 — U3를 다시 만들면 관리자가 직접 고친 줄 수를 알려 준다")
	void get_succeeded_showsPreview() throws Exception {
		long id = start("{\"units\": [\"U3\"], \"cascade\": false}");
		when(pipelineClient.getJob(anyString())).thenReturn(job("SUCCEEDED"));
		when(pipelineClient.getRegenerationResult(anyString())).thenReturn(result("U3", """
			[{"factorId": 1, "role": "prosecutor", "text": "새 줄 하나.", "validation": {"ok": true}},
			 {"factorId": 2, "role": "defense", "text": "새 줄 둘.", "validation": {"ok": true}}]""", 4));

		perform(get(regenerationsUrl() + "/" + id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.cascade").value(false))
			.andExpect(jsonPath("$.applicable").value(true))
			.andExpect(jsonPath("$.preview.regenerated[0]").value("U3"))
			.andExpect(jsonPath("$.preview.manualLinesReplaced").value(1))
			.andExpect(jsonPath("$.preview.blocks[0].revision").value(4))
			.andExpect(jsonPath("$.stages[0].name").value("script"));	// 꺼진 등록 단계는 저장하지 않는다
	}

	@Test
	@DisplayName("A-13-17: 다시 만든 블록만 바꾸고 그 블록의 승인을 지운다 — 사건 내용 · 기사 판을 채우고, 다른 블록 승인은 그대로다")
	void apply_replacesOnlyRegeneratedBlocks() throws Exception {
		jdbcTemplate.update("UPDATE case_script_block SET review_status = 'APPROVED', reviewed_at = now() WHERE script_id = ?",
			scriptId);
		long id = start("{\"units\": [\"U4\"]}");
		when(pipelineClient.getJob(anyString())).thenReturn(job("SUCCEEDED"));
		when(pipelineClient.getRegenerationResult(anyString())).thenReturn(result("U4", NEW_CLOSING, 2));

		perform(post(regenerationsUrl() + "/" + id + "/apply"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.blocks[4].unit").value("U4"))
			.andExpect(jsonPath("$.blocks[4].content.prosecutor").value("검사 측 의견입니다. 새 의견."))
			.andExpect(jsonPath("$.blocks[4].revision").value(2))
			.andExpect(jsonPath("$.blocks[4].basis.case").value(1))
			.andExpect(jsonPath("$.blocks[4].reviewStatus").value("PENDING"))
			.andExpect(jsonPath("$.blocks[0].reviewStatus").value("APPROVED"))
			.andExpect(jsonPath("$.model").value("openai:test-script"));

		assertThat(jdbcTemplate.queryForObject("SELECT applied_at IS NOT NULL FROM case_script_regeneration WHERE id = ?",
			Boolean.class, id)).isTrue();
		verify(pipelineClient).deleteJobQuietly("job-1");
		assertThat(auditActions()).containsExactly("CASE_SCRIPT_REGENERATE", "CASE_SCRIPT_REGENERATE_APPLY");
		// 같은 결과를 두 번 반영하지 않는다
		perform(post(regenerationsUrl() + "/" + id + "/apply")).andExpect(status().isConflict());
	}

	@Test
	@DisplayName("A-13-17 (BE-163): 다시 만들지 않은 블록은 결과의 unchangedWarnings로 경고만 바꾸고 승인은 그대로 둔다")
	void apply_replacesWarningsOfUnchangedBlocks() throws Exception {
		jdbcTemplate.update("UPDATE case_script_block SET review_status = 'APPROVED', reviewed_at = now() WHERE script_id = ?",
			scriptId);
		jdbcTemplate.update("""
			UPDATE case_script_block
			SET review = '[{"unit": "U2", "path": "/plea/0", "code": "FACT_AS_CLAIM", "severity": "warning"}]'::jsonb
			WHERE script_id = ? AND unit = 'U2'""", scriptId);
		long id = start("{\"units\": [\"U4\"]}");
		when(pipelineClient.getJob(anyString())).thenReturn(job("SUCCEEDED"));
		ObjectNode result = (ObjectNode) result("U4", NEW_CLOSING, 2);
		result.set("unchangedWarnings", MAPPER.readTree("""
			{"U2": [{"unit": "U2", "path": "", "code": "CARD_LIST_EMPTY", "severity": "warning", "fix": null,
			         "message": "쟁점 카드 목록(ul)이 비었습니다"}],
			 "U0": [], "U1": null, "U4": "x", "U9": [{"severity": "warning"}]}"""));
		when(pipelineClient.getRegenerationResult(anyString())).thenReturn(result);

		perform(post(regenerationsUrl() + "/" + id + "/apply"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.blocks[2].unit").value("U2"))
			.andExpect(jsonPath("$.blocks[2].review.length()").value(1))
			.andExpect(jsonPath("$.blocks[2].review[0].code").value("CARD_LIST_EMPTY"))	// 옛 경고는 새 경고로 바뀐다
			.andExpect(jsonPath("$.blocks[2].reviewStatus").value("APPROVED"))
			.andExpect(jsonPath("$.blocks[2].revision").value(1))
			.andExpect(jsonPath("$.blocks[4].reviewStatus").value("PENDING"));
		// 감사 로그에는 다시 만들지 않았는데 경고가 바뀐 블록을 남긴다 — U0은 빈 목록 그대로, U1은 모양이 틀려 건너뛰었다
		assertThat(jdbcTemplate.queryForObject("""
			SELECT detail->'warningUnits' FROM admin_audit_log WHERE action = 'CASE_SCRIPT_REGENERATE_APPLY'
			ORDER BY id DESC LIMIT 1""", String.class)).isEqualTo("[\"U2\"]");
	}

	@Test
	@DisplayName("A-13-17: 요청한 뒤 대사를 고치거나 배역을 바꾸면 옛 입력으로 만든 결과라 반영하지 않는다")
	void apply_afterInputsChanged_isConflict() throws Exception {
		long id = start("{\"units\": [\"U4\"]}");
		when(pipelineClient.getJob(anyString())).thenReturn(job("SUCCEEDED"));
		when(pipelineClient.getRegenerationResult(anyString())).thenReturn(result("U4", NEW_CLOSING, 2));
		perform(patch("/api/v1/admin/cases/" + caseId + "/script/evidence/2").contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\": \"관리자가 다시 고친 줄입니다.\"}"))
			.andExpect(status().isOk());

		perform(get(regenerationsUrl() + "/" + id))
			.andExpect(jsonPath("$.applicable").value(false))
			.andExpect(jsonPath("$.notApplicable").value(AdminScriptRegenerationService.INPUTS_CHANGED));
		perform(post(regenerationsUrl() + "/" + id + "/apply"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(AdminScriptRegenerationService.INPUTS_CHANGED));
		assertThat(jdbcTemplate.queryForObject("""
			SELECT b.content ->> 'prosecutor' FROM case_script_block b WHERE b.script_id = ? AND b.unit = 'U4'""",
			String.class, scriptId)).isEqualTo("옛 의견");
	}

	@Test
	@DisplayName("A-13-17: 요청한 뒤 섹션 · AI 판결 형량을 바꿔도 반영하지 않는다 — 사건 내용 판이 오르지 않는 입력도 해시로 본다")
	void apply_afterSectionOrJudgmentChanged_isConflict() throws Exception {
		long id = start("{\"units\": [\"U4\"]}");
		when(pipelineClient.getJob(anyString())).thenReturn(job("SUCCEEDED"));
		when(pipelineClient.getRegenerationResult(anyString())).thenReturn(result("U4", NEW_CLOSING, 2));
		jdbcTemplate.update("UPDATE case_section SET content = '고친 사실관계' WHERE case_id = ? AND section_type = 'FACTS'", caseId);
		perform(get(regenerationsUrl() + "/" + id))
			.andExpect(jsonPath("$.notApplicable").value(AdminScriptRegenerationService.INPUTS_CHANGED));
		jdbcTemplate.update("UPDATE case_section SET content = '말다툼 끝에 흉기를 들었다.' WHERE case_id = ? AND section_type = 'FACTS'",
			caseId);
		perform(get(regenerationsUrl() + "/" + id)).andExpect(jsonPath("$.applicable").value(true));	// 되돌리면 같은 입력이다
		jdbcTemplate.update("UPDATE judgment SET prison_months = 120 WHERE case_id = ?", caseId);
		perform(post(regenerationsUrl() + "/" + id + "/apply"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(AdminScriptRegenerationService.INPUTS_CHANGED));
	}

	@Test
	@DisplayName("A-13-17: 공개 중인 사건도 반영한다 — 나가 있던 대본은 점검 중(API 18 503)이 되고 감사 로그에 남는다 (BE-126 2차-2)")
	void apply_publishedCase_putsScriptUnderMaintenance() throws Exception {
		jdbcTemplate.update("UPDATE legal_case SET status = 'PUBLISHED', published_at = now() WHERE id = ?", caseId);
		jdbcTemplate.update("UPDATE case_script_block SET review_status = 'APPROVED', reviewed_by_admin_id = ?, reviewed_at = now() "
			+ "WHERE script_id = ?", admin.getId(), scriptId);
		jdbcTemplate.update("UPDATE case_script SET released_at = now() WHERE id = ?", scriptId);
		long id = start("{\"units\": [\"U4\"]}");
		when(pipelineClient.getJob(anyString())).thenReturn(job("SUCCEEDED"));
		when(pipelineClient.getRegenerationResult(anyString())).thenReturn(result("U4", NEW_CLOSING, 2));

		perform(get(regenerationsUrl() + "/" + id)).andExpect(jsonPath("$.applicable").value(true));
		perform(post(regenerationsUrl() + "/" + id + "/apply"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scriptStatus").value("MAINTENANCE"))
			.andExpect(jsonPath("$.blocks[4].reviewStatus").value("PENDING"));

		mockMvc.perform(get("/api/v1/cases/" + caseId + "/script"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("SCRIPT_UNDER_MAINTENANCE"));
		assertThat(jdbcTemplate.queryForObject("""
			SELECT detail ->> 'scriptMaintenance' FROM admin_audit_log
			WHERE actor_email = ? AND action = 'CASE_SCRIPT_REGENERATE_APPLY'""", String.class, admin.getEmail()))
			.isEqualTo("true");
	}

	@Test
	@DisplayName("A-13-16: 결과를 가져오지 못하면 반영할 수 없다고 알린다 — 수동 수정 줄 수 없이 반영 버튼이 켜지지 않게")
	void get_resultUnavailable_isNotApplicable() throws Exception {
		long id = start("{\"units\": [\"U3\"]}");
		when(pipelineClient.getJob(anyString())).thenReturn(job("SUCCEEDED"));
		when(pipelineClient.getRegenerationResult(anyString())).thenThrow(new PipelineException("닿지 못함"));

		perform(get(regenerationsUrl() + "/" + id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.applicable").value(false))
			.andExpect(jsonPath("$.notApplicable").value(AdminScriptRegenerationService.RESULT_UNAVAILABLE))
			.andExpect(jsonPath("$.preview").doesNotExist());
	}

	@Test
	@DisplayName("A-13-15: 사건 배역이 대본 배역과 다르면 배역이 말하는 U2 · U3 · U4를 모두 다시 만들어야 한다")
	void start_castChanged_requiresCastBlocks() throws Exception {
		jdbcTemplate.update("UPDATE legal_case SET cast_roles = '{\"prosecutor\": \"nambul\", \"defense\": \"naero\"}'::jsonb WHERE id = ?",
			caseId);
		for (String body : List.of("{\"units\": [\"U1\"]}", "{\"units\": [\"U2\", \"U3\"], \"cascade\": false}")) {
			perform(post(regenerationsUrl()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(AdminScriptRegenerationService.CAST_CHANGED));
		}
		// cascade면 U2를 고를 때 U4가 따라온다
		start("{\"units\": [\"U2\", \"U3\"]}");
	}

	@Test
	@DisplayName("A-13-17: 더 최근 재생성이 있으면 옛 작업은 반영할 수 없다")
	void apply_notLatest_isConflict() throws Exception {
		when(pipelineClient.getJob(anyString())).thenReturn(job("SUCCEEDED"));
		long older = start("{\"units\": [\"U4\"]}");
		start("{\"units\": [\"U2\"]}");
		when(pipelineClient.getRegenerationResult(anyString())).thenReturn(result("U4", NEW_CLOSING, 2));

		perform(post(regenerationsUrl() + "/" + older + "/apply"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(AdminScriptRegenerationService.NOT_LATEST));
	}

	@Test
	@DisplayName("A-13-15: 같은 사건의 재생성이 아직 돌면 409 — 파이프라인에 한 번 확인한 뒤 막는다")
	void start_whileRunning_isConflict() throws Exception {
		start("{\"units\": [\"U4\"]}");
		when(pipelineClient.getJob(anyString())).thenReturn(job("RUNNING"));
		perform(post(regenerationsUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"units\": [\"U2\"]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IMPORT_IN_PROGRESS"));
	}

	@Test
	@DisplayName("A-13-16: 다른 사건의 작업 번호로 물으면 404")
	void get_otherCase_isNotFound() throws Exception {
		long id = start("{\"units\": [\"U4\"]}");
		long other = insertCase("MURDER", "DRAFT");
		perform(get("/api/v1/admin/cases/" + other + "/script/regenerations/" + id))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SCRIPT_REGENERATION_NOT_FOUND"));
	}

	@Test
	@DisplayName("A-11: 분류 네 축을 통째로 고친다 — 아는 값만, 확인 필요 목록에서 분류 축 항목을 지운다(민감 플래그 항목은 남긴다)")
	void editCase_classification() throws Exception {
		perform(patch("/api/v1/admin/cases/" + caseId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"classification\": {\"mood\": \"cold\", \"issue\": \"denied\", \"victim\": \"mixed\", \"thirdParty\": true}}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.classification.mood").value("cold"))
			.andExpect(jsonPath("$.content.classification.thirdParty").value(true))
			.andExpect(jsonPath("$.content.classificationNeedsReview[0]").value("sensitiveFlags.SELF_HARM"))
			.andExpect(jsonPath("$.content.classificationNeedsReview.length()").value(1));
		for (String bad : List.of("{\"classification\": null}", "{\"classification\": {\"mood\": \"cold\"}}",
				"{\"classification\": {\"mood\": \"angry\", \"issue\": \"denied\", \"victim\": \"mixed\", \"thirdParty\": true}}",
				"{\"classification\": {\"mood\": \"cold\", \"issue\": \"denied\", \"victim\": \"mixed\", \"thirdParty\": \"yes\"}}")) {
			perform(patch("/api/v1/admin/cases/" + caseId).contentType(MediaType.APPLICATION_JSON).content(bad))
				.andExpect(status().isBadRequest());
		}
		assertThat(jdbcTemplate.queryForObject("SELECT detail::text FROM admin_audit_log WHERE actor_email = ? AND action = 'CASE_UPDATE'",
			String.class, admin.getEmail())).contains("classification").contains("cold");
	}

	// --- 도움 함수 ---

	private long start(String body) throws Exception {
		MvcResult result = perform(post(regenerationsUrl()).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isAccepted()).andReturn();
		return MAPPER.readTree(result.getResponse().getContentAsString()).get("regenerationId").asLong();
	}

	private void expectConflict(String message) throws Exception {
		perform(post(regenerationsUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"units\": [\"U4\"]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains(message));
	}

	private int jobCount;

	private PipelineJob job(String status) {
		String id = "job-" + (status.equals("QUEUED") ? ++jobCount : Math.max(jobCount, 1));
		return new PipelineJob(id, status, List.of(new PipelineJob.Stage("extract", "SKIPPED", null),
			new PipelineJob.Stage("script", status.equals("SUCCEEDED") ? "DONE" : "RUNNING", null)), null,
			null, null, status.equals("SUCCEEDED") ? "2026-11-02T10:00:00+09:00" : null);
	}

	/** 서비스 결과의 {@code regenerated} — 블록 하나를 다시 만든 모양 */
	private static JsonNode result(String unit, String content, int revision) {
		return MAPPER.readTree("""
			{"units": ["%1$s"], "regenerated": ["%1$s"], "pending": [], "stopped": false, "publishable": true,
			 "blocks": {"%1$s": {"content": %2$s, "revision": %3$d, "basis": {"case": null, "cast": %4$s},
			                     "review": [], "warnings": [], "repairs": []}},
			 "meta": {"cast": %4$s, "issue": "admitted", "admits": true}, "review": [], "repairs": [],
			 "model": "openai:test-script"}""".formatted(unit, content, revision, CAST));
	}

	private void insertBlock(String unit, String content, int revision) {
		jdbcTemplate.update("""
			INSERT INTO case_script_block (script_id, unit, content, revision, publishable, basis)
			VALUES (?, ?, ?::jsonb, ?, true, '{"case": 1}'::jsonb)""", scriptId, unit, content, revision);
	}

	private String regenerationsUrl() {
		return "/api/v1/admin/cases/" + caseId + "/script/regenerations";
	}

	private List<String> auditActions() {
		return jdbcTemplate.queryForList("""
			SELECT action FROM admin_audit_log WHERE actor_email = ? AND action LIKE 'CASE_SCRIPT_REGENERATE%' ORDER BY id""",
			String.class, admin.getEmail());
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
