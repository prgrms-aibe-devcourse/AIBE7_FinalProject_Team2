package com.team2.project.judgment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.common.exception.ApiExceptionAdvice;
import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import com.team2.project.judgment.domain.InvalidJudgmentException;
import com.team2.project.judgment.dto.VerdictResponse;
import com.team2.project.judgment.service.VerdictFormService;
import com.team2.project.judgment.service.VerdictService;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** API 8 · 9 경로 · 쿠키 전달 · 에러 응답 변환. 서비스 동작은 VerdictServiceTest에서 검증한다 */
class VerdictControllerTest {
	private static final UUID ANONYMOUS_ID = UUID.randomUUID();
	private static final String VALID = """
		{"penaltyType":"LIFE","reducedTo":"PRISON","prisonMonths":480,"fineAmount":null,"suspensionMonths":null,
		 "factors":[{"factorId":2,"direction":"DOWN"}]}
		""";
	private final VerdictFormService formService = mock(VerdictFormService.class);
	private final VerdictService verdictService = mock(VerdictService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new VerdictController(formService, verdictService, new AnonymousIdCookie()))
			.setControllerAdvice(new ApiExceptionAdvice())
			.build();
	}

	private static Cookie cookie() {
		return new Cookie(AnonymousIdCookie.NAME, ANONYMOUS_ID.toString());
	}

	@Test
	void getForm_wrongState_returns409WithCurrentStatus() throws Exception {
		when(formService.getForm(7L, Optional.of(ANONYMOUS_ID)))
			.thenThrow(new InvalidExperienceStateException(ExperienceStatus.REVIEWING, "x"));
		mockMvc.perform(get("/api/v1/cases/7/experience/verdict-form").cookie(cookie()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.currentStatus").value("REVIEWING"));
	}

	@Test
	void submit_valid_returns200VerdictConfirmed() throws Exception {
		when(verdictService.submit(eq(7L), eq(Optional.of(ANONYMOUS_ID)), any()))
			.thenReturn(new VerdictResponse(ExperienceStatus.VERDICT_CONFIRMED));
		mockMvc.perform(post("/api/v1/cases/7/experience/verdict").cookie(cookie())
				.contentType(MediaType.APPLICATION_JSON).content(VALID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("VERDICT_CONFIRMED"));
	}

	@Test
	void submit_outOfRange_returns422WithFieldDetail() throws Exception {
		when(verdictService.submit(eq(7L), eq(Optional.of(ANONYMOUS_ID)), any())).thenThrow(new InvalidJudgmentException(
			InvalidJudgmentException.Reason.OUT_OF_ALLOWED_RANGE, "x", "prisonMonths"));
		mockMvc.perform(post("/api/v1/cases/7/experience/verdict").cookie(cookie())
				.contentType(MediaType.APPLICATION_JSON).content(VALID))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("OUT_OF_ALLOWED_RANGE"))
			.andExpect(jsonPath("$.details[0].field").value("prisonMonths"))
			.andExpect(jsonPath("$.details[0].reason").value("OUT_OF_ALLOWED_RANGE"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{\"penaltyType\":\"\"}",
		"{\"penaltyType\":\"PRISON\",\"prisonMonths\":-1}",
		"{\"penaltyType\":\"PRISON\",\"prisonMonths\":36,\"freeOpinion\":\"의견\"}",
		"{\"penaltyType\":\"PRISON\",\"prisonMonths\":36,\"factors\":[null]}",
		"not json"
	})
	void submit_malformed_returns400BeforeService(String body) throws Exception {
		mockMvc.perform(post("/api/v1/cases/7/experience/verdict").cookie(cookie())
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		verifyNoInteractions(verdictService);
	}
}
