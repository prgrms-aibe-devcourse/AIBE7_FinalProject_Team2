package com.team2.project.experience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;

import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.support.ApiIntegrationTest;

import jakarta.servlet.http.Cookie;

/** BE-77: API 3의 본인 사전 판단 조회와 API 2 계약 회귀. */
class ExperienceStatusApiTest extends ApiIntegrationTest {

	@Test
	void beforeSubmission_returnsExplicitNullAndOnlyStatusFields() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);

		mockMvc.perform(get(experienceUrl(caseId)).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.caseId").value(caseId))
			.andExpect(jsonPath("$.status").value("STARTED"))
			.andExpect(jsonPath("$.preJudgment").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.preJudgment").hasJsonPath())
			.andExpect(jsonPath("$.*").value(org.hamcrest.Matchers.hasSize(6)));
	}

	@ParameterizedTest
	@EnumSource(value = ExperienceStatus.class, names = "STARTED", mode = EnumSource.Mode.EXCLUDE)
	void afterSubmission_returnsOwnRangeWithoutChangingState(ExperienceStatus state) throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = startAndGetCookie(caseId);
		long rangeId = rangeOptionIds("MURDER").get(3);
		mockMvc.perform(post(experienceUrl(caseId) + "/pre-judgment").cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON).content("{\"rangeOptionId\":" + rangeId + "}"))
			.andExpect(status().isOk());
		jdbcTemplate.update("UPDATE experience SET status = ? WHERE case_id = ?", state.name(), caseId);
		String before = jdbcTemplate.queryForObject(
			"SELECT row(status, last_reviewed_step, updated_at)::text FROM experience WHERE case_id = ?", String.class, caseId);

		mockMvc.perform(get(experienceUrl(caseId)).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(state.name()))
			.andExpect(jsonPath("$.preJudgment.rangeOptionId").value(rangeId))
			.andExpect(jsonPath("$.preJudgment.label").value("실형 5년 이상 ~ 10년 미만"))
			.andExpect(jsonPath("$.preJudgment.*").value(org.hamcrest.Matchers.hasSize(2)))
			.andExpect(jsonPath("$.*").value(org.hamcrest.Matchers.hasSize(6)));
		assertThat(jdbcTemplate.queryForObject(
			"SELECT row(status, last_reviewed_step, updated_at)::text FROM experience WHERE case_id = ?", String.class, caseId))
			.isEqualTo(before);
	}

	@Test
	void sameCaseDifferentUsers_returnsEachUsersOwnJudgment() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie first = startAndGetCookie(caseId);
		Cookie second = startAndGetCookie(caseId);
		long firstRange = rangeOptionIds("MURDER").get(1);
		long secondRange = rangeOptionIds("MURDER").get(4);
		for (Cookie cookie : new Cookie[] {first, second}) {
			long range = cookie == first ? firstRange : secondRange;
			mockMvc.perform(post(experienceUrl(caseId) + "/pre-judgment").cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON).content("{\"rangeOptionId\":" + range + "}"))
				.andExpect(status().isOk());
			mockMvc.perform(get(experienceUrl(caseId)).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.preJudgment.rangeOptionId").value(range));
		}
		mockMvc.perform(get(experienceUrl(caseId)).cookie(first))
			.andExpect(jsonPath("$.preJudgment.rangeOptionId").value(firstRange));
		Cookie stranger = startAndGetCookie(insertCase("FRAUD", "PUBLISHED"));
		mockMvc.perform(get(experienceUrl(caseId)).cookie(stranger))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EXPERIENCE_NOT_FOUND"));
	}

	@Test
	void startResponse_keepsExactlyFiveFieldsForNewAndExistingExperience() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie cookie = new Cookie(COOKIE, UUID.randomUUID().toString());
		jdbcTemplate.update("INSERT INTO anonymous_user (id) VALUES (?)", UUID.fromString(cookie.getValue()));
		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(post(experienceUrl(caseId)).cookie(cookie))
				.andExpect(status().is(attempt == 0 ? 201 : 200))
				.andExpect(jsonPath("$.*").value(org.hamcrest.Matchers.hasSize(5)))
				.andExpect(jsonPath("$.preJudgment").doesNotHaveJsonPath());
		}
	}

	@Test
	void submittedStateWithoutJudgment_returnsGenericInternalError() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		UUID user = UUID.randomUUID();
		insertExperience(user, caseId, 1, "PRE_JUDGED");
		mockMvc.perform(get(experienceUrl(caseId)).cookie(new Cookie(COOKIE, user.toString())))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
	}
}
