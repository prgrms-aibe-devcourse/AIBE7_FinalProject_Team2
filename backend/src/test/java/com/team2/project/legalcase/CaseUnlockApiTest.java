package com.team2.project.legalcase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.team2.project.support.ApiIntegrationTest;

import jakarta.servlet.http.Cookie;

/**
 * 사건 순차 해금 (BE-146): 목록 · 상세의 locked, 목록의 unlock, 체험 시작(API 2)의 409 CASE_LOCKED.
 * 해금은 전체 공개 사건 기준이라, 시험 사건에 unlock_order 1 ~ 4를 줘서 다른 사건(순서 값 없음)보다 앞에 세운다
 */
class CaseUnlockApiTest extends ApiIntegrationTest {

	private static final String CASE_BY_ID = "$.cases[?(@.caseId == %d)]";

	/** 체험할 수 있는(대본 READY) 공개 사건 4건, 해금 순서 1 ~ 4 */
	private long[] readyCases() {
		long[] ids = new long[4];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = insertCase("FRAUD", "PUBLISHED");
			insertScriptWithReview(ids[i], "APPROVED");
			jdbcTemplate.update("UPDATE legal_case SET unlock_order = ? WHERE id = ?", i + 1, ids[i]);
		}
		return ids;
	}

	@Test
	@DisplayName("처음 방문(쿠키 없음): 순서 앞의 3건만 열리고 4번째는 잠긴다. 준비 중 사건은 잠기지 않는다")
	void guestSeesFirstThreeOpen() throws Exception {
		long[] ready = readyCases();
		long preparing = insertCase("FRAUD", "PUBLISHED");
		jdbcTemplate.update("UPDATE legal_case SET unlock_order = 1 WHERE id = ?", preparing);	// 준비 중은 순서 자리를 차지하지 않는다

		mockMvc.perform(get("/api/v1/cases"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unlock.completedCases").value(0))
				.andExpect(jsonPath("$.unlock.nextStepAt").value(1))
				.andExpect(jsonPath(CASE_BY_ID.formatted(ready[2]) + ".locked").value(false))
				.andExpect(jsonPath(CASE_BY_ID.formatted(ready[3]) + ".locked").value(true))
				.andExpect(jsonPath(CASE_BY_ID.formatted(preparing) + ".locked").value(false));

		mockMvc.perform(get("/api/v1/cases/{caseId}", ready[3]))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.locked").value(true));
	}

	@Test
	@DisplayName("1건 완료하면 다음 단계(6건)가 열려 4번째도 열린다. 해금은 crimeType 필터와 상관없다")
	void completingOneOpensNextStep() throws Exception {
		long[] ready = readyCases();
		UUID me = UUID.randomUUID();
		insertExperience(me, ready[0], 1, "COMPLETED");
		Cookie cookie = new Cookie(COOKIE, me.toString());

		mockMvc.perform(get("/api/v1/cases").param("crimeType", "FRAUD").cookie(cookie))
				.andExpect(jsonPath("$.unlock.completedCases").value(1))
				.andExpect(jsonPath("$.unlock.nextStepAt").value(3))
				.andExpect(jsonPath(CASE_BY_ID.formatted(ready[3]) + ".locked").value(false));
		mockMvc.perform(post(experienceUrl(ready[3])).cookie(cookie)).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("잠긴 사건으로 새 체험을 시작하면 409 CASE_LOCKED, 체험도 쿠키도 만들지 않는다")
	void startLockedCaseIsRejected() throws Exception {
		long[] ready = readyCases();

		String setCookie = mockMvc.perform(post(experienceUrl(ready[3])))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CASE_LOCKED"))
				.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).isNull();
		assertThat(countExperiences(ready[3])).isZero();
	}

	@Test
	@DisplayName("이미 체험한 사건은 잠김 순서 밖이어도 열려 있고 이어서 할 수 있다(가져온 체험 등)")
	void touchedCaseStaysOpen() throws Exception {
		long[] ready = readyCases();
		UUID me = UUID.randomUUID();
		insertExperience(me, ready[3], 1, "STARTED");
		Cookie cookie = new Cookie(COOKIE, me.toString());

		mockMvc.perform(get("/api/v1/cases").cookie(cookie))
				.andExpect(jsonPath(CASE_BY_ID.formatted(ready[3]) + ".locked").value(false));
		mockMvc.perform(get("/api/v1/cases/{caseId}", ready[3]).cookie(cookie))
				.andExpect(jsonPath("$.locked").value(false));
		mockMvc.perform(post(experienceUrl(ready[3])).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}
}
