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

	/** 적재(A-14-1) · 검수(A-13-11/12)가 끝난 모양 그대로 한 건 만든다. reviewStatus가 null이면 검수 대기(PENDING) */
	private void insertCaseScript(long caseId, String reviewStatus) {
		jdbcTemplate.update("""
			INSERT INTO case_script (case_id, evidence, model, generated_at, stopped, cast_roles, publishable,
			                         review_status)
			VALUES (?, ?::jsonb, 'google:gemini-2.5-flash', now(), false, ?::jsonb, true, ?)""",
			caseId, EVIDENCE, CAST, reviewStatus);
	}
}
