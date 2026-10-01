package com.team2.project.experience.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.common.exception.ApiExceptionAdvice;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import com.team2.project.experience.domain.ReviewStepOutOfOrderException;
import com.team2.project.experience.dto.ReviewResponse;
import com.team2.project.experience.dto.ReviewStepRequest;
import com.team2.project.experience.dto.ReviewStepResponse;
import com.team2.project.experience.service.ReviewService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** API 6 · 7 경로 · 쿠키 전달 · 에러 응답 변환. 서비스 동작은 ReviewServiceTest에서 검증한다 */
class ReviewControllerTest {
	private static final UUID ANONYMOUS_ID = UUID.randomUUID();
	private final ReviewService service = mock(ReviewService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(service, new AnonymousIdCookie()))
			.setControllerAdvice(new ApiExceptionAdvice())
			.build();
	}

	private static Cookie cookie() {
		return new Cookie(AnonymousIdCookie.NAME, ANONYMOUS_ID.toString());
	}

	@Test
	void getReview_withCookie_passesAnonymousIdAndReturns200() throws Exception {
		when(service.getReview(7L, Optional.of(ANONYMOUS_ID))).thenReturn(
			new ReviewResponse(ExperienceStatus.PRE_JUDGED, 1, 2, List.of(), List.of(3, 4), null, null));
		mockMvc.perform(get("/api/v1/cases/7/experience/review").cookie(cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PRE_JUDGED"))
			.andExpect(jsonPath("$.openStep").value(2))
			.andExpect(jsonPath("$.lockedSteps[0]").value(3));
	}

	@Test
	void getReview_noExperience_returns404() throws Exception {
		when(service.getReview(7L, Optional.empty())).thenThrow(new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND));
		mockMvc.perform(get("/api/v1/cases/7/experience/review"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("EXPERIENCE_NOT_FOUND"));
	}

	@Test
	void getReview_wrongState_returns409WithCurrentStatus() throws Exception {
		when(service.getReview(7L, Optional.of(ANONYMOUS_ID)))
			.thenThrow(new InvalidExperienceStateException(ExperienceStatus.STARTED, "x"));
		mockMvc.perform(get("/api/v1/cases/7/experience/review").cookie(cookie()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.currentStatus").value("STARTED"));
	}

	@Test
	void confirmStep_valid_returns200() throws Exception {
		when(service.confirmStep(7L, Optional.of(ANONYMOUS_ID), new ReviewStepRequest(3)))
			.thenReturn(new ReviewStepResponse(ExperienceStatus.REVIEWING, 3, 4));
		mockMvc.perform(post("/api/v1/cases/7/experience/review-steps").cookie(cookie())
				.contentType(MediaType.APPLICATION_JSON).content("{\"step\":3}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REVIEWING"))
			.andExpect(jsonPath("$.lastReviewedStep").value(3))
			.andExpect(jsonPath("$.openStep").value(4));
	}

	@ParameterizedTest
	@ValueSource(strings = {"{\"step\":1}", "{\"step\":5}", "{}", "{\"step\":null}"})
	void confirmStep_invalidStep_returns400BeforeService(String body) throws Exception {
		mockMvc.perform(post("/api/v1/cases/7/experience/review-steps").cookie(cookie())
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		verifyNoInteractions(service);
	}

	@Test
	void confirmStep_skipped_returns409StepOutOfOrder() throws Exception {
		when(service.confirmStep(eq(7L), eq(Optional.of(ANONYMOUS_ID)), any()))
			.thenThrow(new ReviewStepOutOfOrderException(2, 4));
		mockMvc.perform(post("/api/v1/cases/7/experience/review-steps").cookie(cookie())
				.contentType(MediaType.APPLICATION_JSON).content("{\"step\":4}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STEP_OUT_OF_ORDER"));
		verify(service).confirmStep(7L, Optional.of(ANONYMOUS_ID), new ReviewStepRequest(4));
	}
}
