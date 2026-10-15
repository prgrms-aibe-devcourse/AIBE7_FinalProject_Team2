package com.team2.project.legalcase;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.team2.project.support.ApiIntegrationTest;

import jakarta.servlet.http.Cookie;

/**
 * API 16. 공개 사건 상세 (S-20 사건 디테일, BE-74).
 * 체험 없이 부르고, 판결 전에 보여서는 안 되는 값(법정형 · 권고 범위 · 판결 · 섹션)은 넣지 않는다(FR-2-8).
 */
class CaseDetailApiTest extends ApiIntegrationTest {

	private static String caseUrl(long caseId) {
		return "/api/v1/cases/" + caseId;
	}

	@Test
	void getCase_returnsCardFieldsWithChargeAndOverview() throws Exception {
		long caseId = insertCase("FRAUD", "PUBLISHED");

		mockMvc.perform(get(caseUrl(caseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.caseId").value(caseId))
				.andExpect(jsonPath("$.title").value(containsString("테스트 사건")))
				.andExpect(jsonPath("$.crimeType").value("FRAUD"))
				.andExpect(jsonPath("$.crimeCategoryLabel").value("재산범죄"))
				.andExpect(jsonPath("$.chargeName").value("테스트죄"))
				.andExpect(jsonPath("$.shortIntro").value("테스트 소개"))
				.andExpect(jsonPath("$.keywords[0]").value("키워드1"))
				.andExpect(jsonPath("$.difficulty").value("MID"))
				.andExpect(jsonPath("$.estimatedMinutes").value(10))
				.andExpect(jsonPath("$.participantCount").value(0))
				.andExpect(jsonPath("$.thumbnailUrl").value((Object) null))
				.andExpect(jsonPath("$.overview").value("테스트 개요"))
				.andExpect(jsonPath("$.myStatus").value((Object) null));
	}

	@Test
	void getCase_hidesValuesBeforeVerdict() throws Exception {
		// 체험 전 화면이라 판결을 미리 알려 주는 값은 없다 (API 4와 같은 기준, 섹션 본문은 API 6에서 단계별로)
		long caseId = insertCase("MURDER", "PUBLISHED");

		mockMvc.perform(get(caseUrl(caseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.appliedLaw").doesNotExist())
				.andExpect(jsonPath("$.statutoryPenaltyText").doesNotExist())
				.andExpect(jsonPath("$.recommendedMinMonths").doesNotExist())
				.andExpect(jsonPath("$.recommendedMaxMonths").doesNotExist())
				.andExpect(jsonPath("$.recommendedBasis").doesNotExist())
				.andExpect(jsonPath("$.sections").doesNotExist())
				.andExpect(jsonPath("$.judgments").doesNotExist())
				.andExpect(jsonPath("$.incidentDate").doesNotExist());
	}

	@Test
	void getCase_draftOrReviewOrMissing_isNotFound() throws Exception {
		long draft = insertCase("MURDER", "DRAFT");
		long review = insertCase("MURDER", "REVIEW");

		for (long caseId : new long[] {draft, review, Long.MAX_VALUE}) {
			mockMvc.perform(get(caseUrl(caseId)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
		}
	}

	@Test
	void getCase_invalidId_isValidationError() throws Exception {
		mockMvc.perform(get("/api/v1/cases/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void getCase_participantCount_countsOnlyCompletedFirstAttempts() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		insertExperience(UUID.randomUUID(), caseId, 1, "COMPLETED");
		insertExperience(UUID.randomUUID(), caseId, 1, "REVIEWING");
		insertExperience(UUID.randomUUID(), caseId, 2, "COMPLETED");

		mockMvc.perform(get(caseUrl(caseId)))
				.andExpect(jsonPath("$.participantCount").value(1));
	}

	@Test
	void getCase_myStatus_followsMyLatestAttempt() throws Exception {
		UUID me = UUID.randomUUID();
		long inProgress = insertCase("MURDER", "PUBLISHED");
		long completed = insertCase("FRAUD", "PUBLISHED");
		long retried = insertCase("INJURY", "PUBLISHED");
		insertExperience(me, inProgress, 1, "AI_REVEALED");	// 실제 판결 공개까지는 진행 중
		insertExperience(me, completed, 1, "COMPLETED");
		insertExperience(me, retried, 1, "COMPLETED");
		insertExperience(me, retried, 2, "STARTED");		// 재체험은 최신 회차 기준 (API 1 · 2와 같음)
		Cookie cookie = new Cookie(COOKIE, me.toString());

		mockMvc.perform(get(caseUrl(inProgress)).cookie(cookie)).andExpect(jsonPath("$.myStatus").value("IN_PROGRESS"));
		mockMvc.perform(get(caseUrl(completed)).cookie(cookie)).andExpect(jsonPath("$.myStatus").value("COMPLETED"));
		mockMvc.perform(get(caseUrl(retried)).cookie(cookie)).andExpect(jsonPath("$.myStatus").value("IN_PROGRESS"));
	}

	@Test
	void getCase_myStatus_ignoresOthersAndBrokenCookies() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		insertExperience(UUID.randomUUID(), caseId, 1, "COMPLETED");	// 다른 사람의 체험

		for (String value : new String[] {UUID.randomUUID().toString(), "not-a-uuid"}) {
			mockMvc.perform(get(caseUrl(caseId)).cookie(new Cookie(COOKIE, value)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.myStatus").value((Object) null));
		}
	}

	@Test
	void getCase_doesNotIssueCookieAndIsNotCacheable() throws Exception {
		// 둘러보기 화면이라 쿠키를 발급하지 않는다(발급은 API 2). myStatus가 사용자마다 달라 저장되면 안 된다 (API 1과 같음)
		UUID me = UUID.randomUUID();
		long caseId = insertCase("MURDER", "PUBLISHED");
		insertExperience(me, caseId, 1, "REVIEWING");

		mockMvc.perform(get(caseUrl(caseId)))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
		mockMvc.perform(get(caseUrl(caseId)).cookie(new Cookie(COOKIE, me.toString())))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
	}

	@Test
	void getCase_matchesStartedExperience() throws Exception {
		// 실제 흐름: S-20에서 체험 시작(API 2) → 같은 쿠키로 S-20을 다시 열면 "이어서 하기"
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);

		mockMvc.perform(get(caseUrl(caseId)).cookie(cookie))
				.andExpect(jsonPath("$.myStatus").value("IN_PROGRESS"));
	}
}
