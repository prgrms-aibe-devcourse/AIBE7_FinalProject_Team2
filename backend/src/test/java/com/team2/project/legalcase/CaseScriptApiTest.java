package com.team2.project.legalcase;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * API 18. 공개 사건 대본 (BE-109). BE-107 파이프라인이 만들고 BE-108에서 승인한 2막 증거조사
 * 대사만 내려준다. 비공개 사건은 CASE_NOT_FOUND, 대본이 없거나 아직 승인 전이면 SCRIPT_NOT_FOUND
 * (해송님 동의, BE-109 Jira 댓글, 2026-10-27).
 */
class CaseScriptApiTest extends ApiIntegrationTest {

	// factorId 9는 사건에 없는 요소다 — card를 못 찾아도 응답이 깨지지 않는지 본다. role은 파이프라인이
	// 소문자로 주므로(script_generation.py ROLE_BY_DIRECTION) 소문자로 적재해, 응답이 대문자로 바뀌는지 본다
	private static final String EVIDENCE = """
		[{"factorId": 1, "role": "prosecutor", "character": "naero", "text": "계약서에 서명한 사실은 분명합니다.",
		  "attempts": 1, "validation": {"warnings": []}, "manual": true, "editedAt": "2026-10-27T00:00:00Z"},
		 {"factorId": 2, "role": "defense", "character": "nambul", "text": "그 돈은 받은 것입니다.",
		  "attempts": 1, "validation": {"warnings": []}},
		 {"factorId": 9, "role": "prosecutor", "character": "naero", "text": "지워진 요소에 붙은 대사입니다.",
		  "attempts": 1, "validation": {"warnings": []}}]""";
	private static final String CAST = "{\"prosecutor\": \"naero\", \"defense\": \"nambul\"}";

	private static String scriptUrl(long caseId) {
		return "/api/v1/cases/" + caseId + "/script";
	}

	@Test
	@DisplayName("승인된 대본은 증거조사 대사(factorId · side · character · card · text)와 배역을 준다")
	void getCaseScript_approvedScript_returnsEvidenceAndCast() throws Exception {
		long caseId = insertCase("FRAUD", "PUBLISHED");
		insertFactor(caseId, "OVERVIEW", "계약서에 서명했다", "서명했다", 1);
		insertFactor(caseId, "DETAIL", "돈을 돌려주지 않았다", null, 2);
		insertCaseScript(caseId, "APPROVED");

		mockMvc.perform(get(scriptUrl(caseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.evidence[0].factorId").value(1))
				.andExpect(jsonPath("$.evidence[0].side").value("PROSECUTOR"))	// 소문자로 적재 → 대문자로 응답
				.andExpect(jsonPath("$.evidence[0].character").value("naero"))
				.andExpect(jsonPath("$.evidence[0].card").value("계약서에 서명했다"))	// 판단 요소 문구
				.andExpect(jsonPath("$.evidence[0].text").value("계약서에 서명한 사실은 분명합니다."))
				// 검수용 값(manual · editedAt · validation · attempts)은 공개 응답에 내려가지 않는다
				.andExpect(jsonPath("$.evidence[0].manual").doesNotExist())
				.andExpect(jsonPath("$.evidence[0].editedAt").doesNotExist())
				.andExpect(jsonPath("$.evidence[0].validation").doesNotExist())
				.andExpect(jsonPath("$.evidence[0].attempts").doesNotExist())
				.andExpect(jsonPath("$.evidence[1].side").value("DEFENSE"))
				.andExpect(jsonPath("$.evidence[2].card").value((Object) null))	// 사건에 없는 요소면 비운다
				.andExpect(jsonPath("$.cast.prosecutor").value("naero"))
				.andExpect(jsonPath("$.cast.defense").value("nambul"));
	}

	@Test
	@DisplayName("순서는 저장된 배열 순서 그대로다 — 재생 순서와 같다")
	void getCaseScript_preservesArrayOrder() throws Exception {
		long caseId = insertCase("FRAUD", "PUBLISHED");
		insertCaseScript(caseId, "APPROVED");

		mockMvc.perform(get(scriptUrl(caseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.evidence[0].factorId").value(1))
				.andExpect(jsonPath("$.evidence[1].factorId").value(2))
				.andExpect(jsonPath("$.evidence[2].factorId").value(9));
	}

	@Test
	@DisplayName("비공개 · 검수 중 · 없는 사건은 404 CASE_NOT_FOUND")
	void getCaseScript_unpublishedOrMissingCase_isCaseNotFound() throws Exception {
		long draft = insertCase("FRAUD", "DRAFT");
		long review = insertCase("FRAUD", "REVIEW");
		insertCaseScript(draft, "APPROVED");	// 사건이 공개 전이면 대본이 승인돼 있어도 CASE_NOT_FOUND

		for (long caseId : new long[] {draft, review, Long.MAX_VALUE}) {
			mockMvc.perform(get(scriptUrl(caseId)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
		}
	}

	@Test
	@DisplayName("공개 사건이어도 대본이 없거나 · 검수 대기 · 반려면 404 SCRIPT_NOT_FOUND")
	void getCaseScript_notApproved_isScriptNotFound() throws Exception {
		long noScript = insertCase("FRAUD", "PUBLISHED");

		long pending = insertCase("FRAUD", "PUBLISHED");
		insertCaseScript(pending, null);

		long rejected = insertCase("FRAUD", "PUBLISHED");
		insertCaseScript(rejected, "REJECTED");

		for (long caseId : new long[] {noScript, pending, rejected}) {
			mockMvc.perform(get(scriptUrl(caseId)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("SCRIPT_NOT_FOUND"));
		}
	}

	@Test
	@DisplayName("블록 대본은 blocks(U1 ~ U4 값) · meta를 더해 준다 — U0 · 검수용 값은 내리지 않고, U2 신문 답은 정규화한다 (BE-126)")
	void getCaseScript_blocks_returnsContentAndMeta() throws Exception {
		long caseId = insertCase("FRAUD", "PUBLISHED");
		long scriptId = insertCaseScript(caseId, "APPROVED");
		jdbcTemplate.update("UPDATE case_script SET issue = 'admitted', admits = true WHERE id = ?", scriptId);
		insertBlock(scriptId, "U0", """
			{"plea": "인정", "questions": [{"q": "질문1", "a": "네"}, {"q": "질문2", "a": "네"}, {"q": "질문3", "a": "예"}]}""");
		insertBlock(scriptId, "U1", """
			{"lines": ["해설 한 줄"]}""");
		insertBlock(scriptId, "U2", """
			{"indict": "공소 요지", "questions": [{"q": "인정합니까?", "a": "...네, 인정합니다."},
			                                     {"q": "예전에도 그랬습니까?", "a": "예전에는 그런 적이 없습니다"},
			                                     {"q": "다투십니까?", "a": "　아니요"}]}""");
		insertBlock(scriptId, "U4", """
			{"prosecutor": "검사 의견", "defense": "변호인 의견"}""");
		jdbcTemplate.update("""
			UPDATE case_script_block SET review = '[{"unit": "U2", "code": "FORMAL_ENDING", "severity": "warning"}]'::jsonb,
			                             repairs = '[{"unit": "U2", "before": "고치기 전"}]'::jsonb
			WHERE script_id = ? AND unit = 'U2'""", scriptId);

		mockMvc.perform(get(scriptUrl(caseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blocks.U0").doesNotExist())	// 설계도는 재료다 — 인정 여부는 meta.admits로
				.andExpect(jsonPath("$.blocks.U1.lines[0]").value("해설 한 줄"))
				.andExpect(jsonPath("$.blocks.U2.indict").value("공소 요지"))
				.andExpect(jsonPath("$.blocks.U2.questions[0].q").value("인정합니까?"))
				.andExpect(jsonPath("$.blocks.U2.questions[0].a").value("…네."))
				.andExpect(jsonPath("$.blocks.U2.questions[1].a").value("…예전에는 그런 적이 없습니다"))	// 접두만 같은 말
				.andExpect(jsonPath("$.blocks.U2.questions[2].a").value("…아닙니다."))	// 전각 공백
				.andExpect(jsonPath("$.blocks.U2.review").doesNotExist())
				.andExpect(jsonPath("$.blocks.U2.repairs").doesNotExist())
				.andExpect(jsonPath("$.blocks.U3[0].factorId").value(1))
				.andExpect(jsonPath("$.blocks.U3[0].role").value("prosecutor"))
				.andExpect(jsonPath("$.blocks.U3[0].text").value("계약서에 서명한 사실은 분명합니다."))
				.andExpect(jsonPath("$.blocks.U3[0].validation").doesNotExist())	// 대사는 factorId · role · text만
				.andExpect(jsonPath("$.blocks.U3[0].character").doesNotExist())
				.andExpect(jsonPath("$.blocks.U4.defense").value("변호인 의견"))
				.andExpect(jsonPath("$.meta.cast.prosecutor").value("naero"))
				.andExpect(jsonPath("$.meta.issue").value("admitted"))
				.andExpect(jsonPath("$.meta.admits").value(true))
				.andExpect(jsonPath("$.evidence[0].side").value("PROSECUTOR"));	// 기존 필드는 그대로

		// 저장값은 모델이 쓴 그대로다 — 정규화는 응답에서만
		org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
				"SELECT content -> 'questions' -> 0 ->> 'a' FROM case_script_block WHERE script_id = ? AND unit = 'U2'",
				String.class, scriptId)).isEqualTo("...네, 인정합니다.");
	}

	@Test
	@DisplayName("증거조사만 만든 대본은 blocks에 U3뿐이고 meta의 issue · admits는 null이다")
	void getCaseScript_evidenceOnly_hasOnlyU3() throws Exception {
		long caseId = insertCase("FRAUD", "PUBLISHED");
		insertCaseScript(caseId, "APPROVED");

		mockMvc.perform(get(scriptUrl(caseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blocks.U3.length()").value(3))
				.andExpect(jsonPath("$.blocks.U1").doesNotExist())
				.andExpect(jsonPath("$.meta.issue").value((Object) null))
				.andExpect(jsonPath("$.meta.admits").value((Object) null));
	}

	@Test
	@DisplayName("승인되지 않은 블록이 하나라도 있으면 공개하지 않는다 — 404 SCRIPT_NOT_FOUND (BE-126)")
	void getCaseScript_oneBlockNotApproved_isScriptNotFound() throws Exception {
		long caseId = insertCase("FRAUD", "PUBLISHED");
		long scriptId = insertCaseScript(caseId, "APPROVED");
		insertScriptBlock(scriptId, "U4", "{\"prosecutor\": \"의견\", \"defense\": \"의견\"}", null);

		mockMvc.perform(get(scriptUrl(caseId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SCRIPT_NOT_FOUND"));
	}

	/**
	 * 적재(A-14-1) · 검수(A-13-11/12)가 끝난 모양 그대로 한 건 만든다 — 대사는 U3 블록(BE-126). reviewStatus가 null이면
	 * 검수 대기(PENDING)
	 */
	private long insertCaseScript(long caseId, String reviewStatus) {
		Long scriptId = jdbcTemplate.queryForObject("""
			INSERT INTO case_script (case_id, model, generated_at, stopped, cast_roles, publishable)
			VALUES (?, 'google:gemini-2.5-flash', now(), false, ?::jsonb, true)
			RETURNING id""", Long.class, caseId, CAST);
		insertScriptBlock(scriptId, "U3", EVIDENCE, reviewStatus);
		return scriptId;
	}

	/** 승인한 블록 하나 */
	private void insertBlock(long scriptId, String unit, String content) {
		insertScriptBlock(scriptId, unit, content, "APPROVED");
	}
}
