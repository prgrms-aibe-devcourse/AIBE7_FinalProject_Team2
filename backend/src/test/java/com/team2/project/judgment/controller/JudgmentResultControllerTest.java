package com.team2.project.judgment.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.common.exception.ApiExceptionAdvice;
import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import com.team2.project.judgment.dto.RevealResponse;
import com.team2.project.judgment.service.JudgmentResultService;
import com.team2.project.judgment.service.RevealService;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** API 10 ~ 13 경로 · 쿠키 전달 · 에러 응답 변환. 서비스 동작은 JudgmentResultApiTest에서 검증한다 */
class JudgmentResultControllerTest {

	private static final UUID ANONYMOUS_ID = UUID.randomUUID();

	private final JudgmentResultService resultService = mock(JudgmentResultService.class);
	private final RevealService revealService = mock(RevealService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders
			.standaloneSetup(new JudgmentResultController(resultService, revealService, new AnonymousIdCookie()))
			.setControllerAdvice(new ApiExceptionAdvice())
			.build();
	}

	private static Cookie cookie() {
		return new Cookie(AnonymousIdCookie.NAME, ANONYMOUS_ID.toString());
	}

	@Test
	void getAiJudgment_beforeVerdict_returns409WithCurrentStatus() throws Exception {
		when(resultService.getAiJudgment(7L, Optional.of(ANONYMOUS_ID)))
			.thenThrow(new InvalidExperienceStateException(ExperienceStatus.REVIEWED, "x"));

		mockMvc.perform(get("/api/v1/cases/7/experience/judgments/ai").cookie(cookie()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.currentStatus").value("REVIEWED"));
	}

	@Test
	void getCourtJudgment_beforeReveal_returns409WithCurrentStatus() throws Exception {
		when(resultService.getCourtJudgment(7L, Optional.of(ANONYMOUS_ID)))
			.thenThrow(new InvalidExperienceStateException(ExperienceStatus.VERDICT_CONFIRMED, "x"));

		mockMvc.perform(get("/api/v1/cases/7/experience/judgments/court").cookie(cookie()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.currentStatus").value("VERDICT_CONFIRMED"));
	}

	@Test
	void revealCourt_withoutBody_returnsStatus() throws Exception {
		when(revealService.revealCourt(7L, Optional.of(ANONYMOUS_ID)))
			.thenReturn(new RevealResponse(ExperienceStatus.AI_REVEALED));

		mockMvc.perform(post("/api/v1/cases/7/experience/court-reveal").cookie(cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("AI_REVEALED"));
	}

	@Test
	void revealComparison_withoutBody_returnsStatus() throws Exception {
		when(revealService.revealComparison(7L, Optional.of(ANONYMOUS_ID)))
			.thenReturn(new RevealResponse(ExperienceStatus.COMPLETED));

		mockMvc.perform(post("/api/v1/cases/7/experience/comparison-reveal").cookie(cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"));
	}

	@Test
	void revealCourt_withoutCookie_passesEmptyAnonymousId() throws Exception {
		when(revealService.revealCourt(7L, Optional.empty()))
			.thenThrow(new InvalidExperienceStateException(ExperienceStatus.STARTED, "x"));

		mockMvc.perform(post("/api/v1/cases/7/experience/court-reveal"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.currentStatus").value("STARTED"));
	}
}
