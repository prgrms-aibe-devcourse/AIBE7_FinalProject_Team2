package com.team2.project.legalcase;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.team2.project.support.ApiIntegrationTest;

class CaseListApiTest extends ApiIntegrationTest {

	private static final String CASE_BY_ID = "$.cases[?(@.caseId == %d)]";

	@Test
	void getCases_publishedOnly_hidesDraftAndReview() throws Exception {
		long published = insertCase("MURDER", "PUBLISHED");
		long draft = insertCase("MURDER", "DRAFT");
		long review = insertCase("MURDER", "REVIEW");

		mockMvc.perform(get("/api/v1/cases"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(CASE_BY_ID.formatted(published)).isNotEmpty())
				.andExpect(jsonPath(CASE_BY_ID.formatted(draft)).isEmpty())
				.andExpect(jsonPath(CASE_BY_ID.formatted(review)).isEmpty());
	}

	@Test
	void getCases_returnsCardFields() throws Exception {
		long caseId = insertCase("FRAUD", "PUBLISHED");

		mockMvc.perform(get("/api/v1/cases"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(CASE_BY_ID.formatted(caseId) + ".crimeType").value("FRAUD"))
				.andExpect(jsonPath(CASE_BY_ID.formatted(caseId) + ".crimeCategoryLabel").value("재산범죄"))
				.andExpect(jsonPath(CASE_BY_ID.formatted(caseId) + ".shortIntro").value("테스트 소개"))
				.andExpect(jsonPath(CASE_BY_ID.formatted(caseId) + ".keywords[0]").value("키워드1"))
				.andExpect(jsonPath(CASE_BY_ID.formatted(caseId) + ".difficulty").value("MID"))
				.andExpect(jsonPath(CASE_BY_ID.formatted(caseId) + ".estimatedMinutes").value(10))
				.andExpect(jsonPath(CASE_BY_ID.formatted(caseId) + ".participantCount").value(0))
				.andExpect(jsonPath("$.cases[?(@.caseId == %d)].thumbnailUrl".formatted(caseId)).value((Object) null));
	}

	@Test
	void getCases_crimeTypeFilter_returnsOnlyThatType() throws Exception {
		long murder = insertCase("MURDER", "PUBLISHED");
		long injury = insertCase("INJURY", "PUBLISHED");

		mockMvc.perform(get("/api/v1/cases").param("crimeType", "INJURY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(CASE_BY_ID.formatted(injury)).isNotEmpty())
				.andExpect(jsonPath(CASE_BY_ID.formatted(murder)).isEmpty())
				.andExpect(jsonPath("$.cases[?(@.crimeType != 'INJURY')]").isEmpty());
	}

	@Test
	void getCases_summary_ignoresFilter() throws Exception {
		insertCase("MURDER", "PUBLISHED");
		insertCase("FRAUD", "PUBLISHED");

		String unfiltered = mockMvc.perform(get("/api/v1/cases")).andReturn().getResponse().getContentAsString();
		String filtered = mockMvc.perform(get("/api/v1/cases").param("crimeType", "MURDER"))
				.andReturn().getResponse().getContentAsString();

		org.junit.jupiter.api.Assertions.assertEquals(summaryOf(unfiltered), summaryOf(filtered));
	}

	@Test
	void getCases_summary_countsAllThreeCrimeTypesEvenWhenEmpty() throws Exception {
		mockMvc.perform(get("/api/v1/cases"))
				.andExpect(jsonPath("$.summary.total").isNumber())
				.andExpect(jsonPath("$.summary.byCrimeType.MURDER").isNumber())
				.andExpect(jsonPath("$.summary.byCrimeType.FRAUD").isNumber())
				.andExpect(jsonPath("$.summary.byCrimeType.INJURY").isNumber());
	}

	@Test
	void getCases_participantCount_countsOnlyCompletedFirstAttempts() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		insertExperience(UUID.randomUUID(), caseId, 1, "COMPLETED");
		insertExperience(UUID.randomUUID(), caseId, 1, "COMPLETED");
		insertExperience(UUID.randomUUID(), caseId, 1, "AI_REVEALED");
		insertExperience(UUID.randomUUID(), caseId, 2, "COMPLETED");

		mockMvc.perform(get("/api/v1/cases"))
				.andExpect(jsonPath(CASE_BY_ID.formatted(caseId) + ".participantCount").value(2));
	}

	@Test
	void getCases_invalidCrimeType_returnsValidationError() throws Exception {
		mockMvc.perform(get("/api/v1/cases").param("crimeType", "THEFT"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	private static String summaryOf(String json) {
		int start = json.indexOf("\"summary\"");
		int end = json.indexOf("\"cases\"");
		return json.substring(start, end);
	}
}
