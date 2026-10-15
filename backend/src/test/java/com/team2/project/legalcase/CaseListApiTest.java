package com.team2.project.legalcase;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.team2.project.support.ApiIntegrationTest;

import jakarta.servlet.http.Cookie;

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

	// ---- 진행 상태 배지 myStatus (FE-19, REQ-010 · FR-1-6) ----

	@Test
	void getCases_myStatus_nullWithoutCookie() throws Exception {
		// 첫 방문(쿠키 없음): 다른 사람이 체험한 사건이라도 내 상태는 없다
		long caseId = insertCase("MURDER", "PUBLISHED");
		insertExperience(UUID.randomUUID(), caseId, 1, "COMPLETED");

		mockMvc.perform(get("/api/v1/cases"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(CASE_BY_ID.formatted(caseId) + ".myStatus").value((Object) null))
				// 목록은 둘러보기 화면이라 쿠키를 발급하지 않는다 (발급은 체험 시작 API 2에서만)
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void getCases_myStatus_showsOnlyMyExperiences() throws Exception {
		UUID me = UUID.randomUUID();
		long inProgress = insertCase("MURDER", "PUBLISHED");
		long completed = insertCase("FRAUD", "PUBLISHED");
		long untouched = insertCase("INJURY", "PUBLISHED");
		insertExperience(me, inProgress, 1, "REVIEWING");
		insertExperience(me, completed, 1, "COMPLETED");
		insertExperience(UUID.randomUUID(), untouched, 1, "COMPLETED");	// 다른 사람의 체험은 내 배지에 나오지 않는다

		mockMvc.perform(get("/api/v1/cases").cookie(new Cookie(COOKIE, me.toString())))
				.andExpect(status().isOk())
				.andExpect(jsonPath(CASE_BY_ID.formatted(inProgress) + ".myStatus").value("IN_PROGRESS"))
				.andExpect(jsonPath(CASE_BY_ID.formatted(completed) + ".myStatus").value("COMPLETED"))
				.andExpect(jsonPath(CASE_BY_ID.formatted(untouched) + ".myStatus").value((Object) null));
	}

	@Test
	void getCases_myStatus_aiRevealedIsStillInProgress() throws Exception {
		// 실제 판결 공개(AI_REVEALED)까지는 진행 중. 세 판결 비교(COMPLETED)까지 가야 완료 배지
		UUID me = UUID.randomUUID();
		long caseId = insertCase("MURDER", "PUBLISHED");
		insertExperience(me, caseId, 1, "AI_REVEALED");

		mockMvc.perform(get("/api/v1/cases").cookie(new Cookie(COOKIE, me.toString())))
				.andExpect(jsonPath(CASE_BY_ID.formatted(caseId) + ".myStatus").value("IN_PROGRESS"));
	}

	@Test
	void getCases_myStatus_followsLatestAttempt() throws Exception {
		// 재체험: 1회차를 끝내고 2회차를 시작했다면 2회차 기준으로 진행 중.
		// API 2(체험 시작)도 최신 회차를 돌려주므로, 배지와 "이어서 하기"가 여는 화면이 어긋나지 않는다
		UUID me = UUID.randomUUID();
		long caseId = insertCase("FRAUD", "PUBLISHED");
		insertExperience(me, caseId, 1, "COMPLETED");
		insertExperience(me, caseId, 2, "STARTED");

		mockMvc.perform(get("/api/v1/cases").cookie(new Cookie(COOKIE, me.toString())))
				.andExpect(jsonPath(CASE_BY_ID.formatted(caseId) + ".myStatus").value("IN_PROGRESS"));
	}

	@Test
	void getCases_myStatus_unknownCookieIsTreatedAsFirstVisit() throws Exception {
		// 형식은 맞지만 없는 익명 ID(지워진 사용자 등): 오류 없이 배지만 없다
		long caseId = insertCase("INJURY", "PUBLISHED");

		mockMvc.perform(get("/api/v1/cases").cookie(new Cookie(COOKIE, UUID.randomUUID().toString())))
				.andExpect(status().isOk())
				.andExpect(jsonPath(CASE_BY_ID.formatted(caseId) + ".myStatus").value((Object) null));
	}

	@Test
	void getCases_myStatus_malformedCookieIsTreatedAsFirstVisit() throws Exception {
		// UUID가 아닌 값(조작 · 손상): 400 · 500 없이 첫 방문처럼 목록만 보인다 (AnonymousIdCookie.read)
		long caseId = insertCase("FRAUD", "PUBLISHED");

		mockMvc.perform(get("/api/v1/cases").cookie(new Cookie(COOKIE, "not-a-uuid")))
				.andExpect(status().isOk())
				.andExpect(jsonPath(CASE_BY_ID.formatted(caseId) + ".myStatus").value((Object) null));
	}

	@Test
	void getCases_myStatus_matchesStartedExperience() throws Exception {
		// 실제 흐름: 체험 시작(API 2)으로 받은 쿠키로 목록을 다시 열면 진행 중 배지가 붙는다
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);

		mockMvc.perform(get("/api/v1/cases").cookie(cookie))
				.andExpect(jsonPath(CASE_BY_ID.formatted(caseId) + ".myStatus").value("IN_PROGRESS"));
	}

	private static String summaryOf(String json) {
		int start = json.indexOf("\"summary\"");
		int end = json.indexOf("\"cases\"");
		return json.substring(start, end);
	}
}
