package com.team2.project.legalcase;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.team2.project.support.ApiIntegrationTest;

/**
 * API 17. 공개 신문 지면 (BE-102). 신문 지면(BE-92) · 발단 · 결론 사진(BE-91 · BE-101)을
 * 그대로 가져다 조립한다. 발단 · 결론 사진은 배열이 아니라 고정 자리로 내려준다(BE-78 2차 검토).
 */
class CasePaperApiTest extends ApiIntegrationTest {

	private static String paperUrl(long caseId) {
		return "/api/v1/cases/" + caseId + "/paper";
	}

	@Test
	void getCasePaper_returnsHeadlineAndPhotoFixedSlots() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		insertCasePaper(caseId, "테스트 헤드라인");
		insertIncidentPhoto(caseId, "ORIGIN", "https://example.test/origin.jpg", "발단 캡션");
		insertIncidentPhoto(caseId, "CONCLUSION", "https://example.test/conclusion.jpg", "결론 캡션");

		mockMvc.perform(get(paperUrl(caseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.desk").value("사회면"))
				.andExpect(jsonPath("$.headline").value("테스트 헤드라인"))
				.andExpect(jsonPath("$.deck").value("테스트 부제"))
				.andExpect(jsonPath("$.photo").value((Object) null))	// thumbnail_url 미설정
				.andExpect(jsonPath("$.caption").value("테스트 캡션"))
				.andExpect(jsonPath("$.article[0]").value("문단1"))
				.andExpect(jsonPath("$.extraPhotos.origin.imageUrl").value("https://example.test/origin.jpg"))
				.andExpect(jsonPath("$.extraPhotos.origin.caption").value("발단 캡션"))
				.andExpect(jsonPath("$.extraPhotos.conclusion.imageUrl").value("https://example.test/conclusion.jpg"))
				.andExpect(jsonPath("$.extraPhotos.conclusion.caption").value("결론 캡션"));
	}

	@Test
	void getCasePaper_missingRole_isNullNotShifted() throws Exception {
		// 발단만 올리고 결론은 안 올린 경우 — 배열이었다면 결론이 0번 자리로 밀려 발단처럼 보일 수 있었다
		long caseId = insertCase("MURDER", "PUBLISHED");
		insertCasePaper(caseId, "테스트 헤드라인");
		insertIncidentPhoto(caseId, "ORIGIN", "https://example.test/origin.jpg", "발단 캡션");

		mockMvc.perform(get(paperUrl(caseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.extraPhotos.origin.imageUrl").value("https://example.test/origin.jpg"))
				.andExpect(jsonPath("$.extraPhotos.conclusion").value((Object) null));
	}

	@Test
	void getCasePaper_noExtraPhotosAtAll_bothSlotsNull() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		insertCasePaper(caseId, "테스트 헤드라인");

		mockMvc.perform(get(paperUrl(caseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.extraPhotos.origin").value((Object) null))
				.andExpect(jsonPath("$.extraPhotos.conclusion").value((Object) null));
	}

	@Test
	void getCasePaper_draftOrReviewOrMissingCase_isCaseNotFound() throws Exception {
		long draft = insertCase("MURDER", "DRAFT");
		long review = insertCase("MURDER", "REVIEW");

		for (long caseId : new long[] {draft, review, Long.MAX_VALUE}) {
			mockMvc.perform(get(paperUrl(caseId)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
		}
	}

	@Test
	void getCasePaper_publishedButPaperNotGenerated_isPaperNotFound() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");	// case_paper를 아직 안 만든 경우

		mockMvc.perform(get(paperUrl(caseId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PAPER_NOT_FOUND"));
	}
}
