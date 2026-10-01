package com.team2.project.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.experience.dto.PreJudgmentRequest;
import com.team2.project.experience.dto.ReviewStepRequest;
import com.team2.project.judgment.dto.VerdictRequest;
import com.team2.project.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON 숫자 입력 정책 (BE-22, B안). 대상: API 5 · 7 · 9 요청 본문의 정수 필드 — Spring Boot가 실제로 쓰는 JsonMapper와 MockMvc로 확인한다.
 * - 정수 필드의 소수(36.7, 36.0)는 거절 → 400 VALIDATION_ERROR
 * - 문자열 숫자("36")는 허용, 숫자로 읽을 수 없는 문자열("삼십육")은 거절
 */
class JsonNumberPolicyTest extends ApiIntegrationTest {

	private static final long MISSING_CASE_ID = 999_999_999L;

	@Autowired
	JsonMapper mapper;

	@Test
	void integer_andNumericString_areAccepted() {
		assertThat(mapper.readValue("{\"step\":3}", ReviewStepRequest.class).step()).isEqualTo(3);
		assertThat(mapper.readValue("{\"step\":\"3\"}", ReviewStepRequest.class).step()).isEqualTo(3);
		assertThat(mapper.readValue("{\"penaltyType\":\"PRISON\",\"prisonMonths\":\"36\"}", VerdictRequest.class)
			.prisonMonths()).isEqualTo(36);
		assertThat(mapper.readValue("{\"penaltyType\":\"FINE\",\"fineAmount\":5000000}", VerdictRequest.class)
			.fineAmount()).isEqualTo(5_000_000L);
		assertThat(mapper.readValue("{\"rangeOptionId\":11,\"factorIds\":[2,\"3\"]}", PreJudgmentRequest.class))
			.isEqualTo(new PreJudgmentRequest(11L, java.util.List.of(2L, 3L)));
	}

	@ParameterizedTest
	@ValueSource(strings = {"{\"rangeOptionId\":11.5}", "{\"rangeOptionId\":11.0}", "{\"rangeOptionId\":11,\"factorIds\":[2.5]}"})
	void preJudgment_float_isRejected(String json) {
		assertThatThrownBy(() -> mapper.readValue(json, PreJudgmentRequest.class)).isInstanceOf(JacksonException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"{\"step\":2.5}", "{\"step\":3.0}", "{\"step\":\"삼\"}"})
	void reviewStep_floatOrNonNumeric_isRejected(String json) {
		assertThatThrownBy(() -> mapper.readValue(json, ReviewStepRequest.class)).isInstanceOf(JacksonException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{\"penaltyType\":\"PRISON\",\"prisonMonths\":36.7}",
		"{\"penaltyType\":\"PRISON\",\"prisonMonths\":36.0}",
		"{\"penaltyType\":\"PRISON\",\"prisonMonths\":\"삼십육\"}",
		"{\"penaltyType\":\"FINE\",\"fineAmount\":5000000.5}",
		"{\"penaltyType\":\"PRISON\",\"prisonMonths\":36,\"factors\":[{\"factorId\":2.5,\"direction\":\"UP\"}]}"
	})
	void verdict_floatOrNonNumeric_isRejected(String json) {
		assertThatThrownBy(() -> mapper.readValue(json, VerdictRequest.class)).isInstanceOf(JacksonException.class);
	}

	@Test
	void http_floatStep_returns400BeforeLookup() throws Exception {
		// 형식 오류는 사건 · 체험 조회보다 먼저 거절된다 (없는 사건이어도 404가 아니라 400)
		mockMvc.perform(post("/api/v1/cases/" + MISSING_CASE_ID + "/experience/review-steps")
				.contentType(MediaType.APPLICATION_JSON).content("{\"step\":2.5}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"pre-judgment|{\"rangeOptionId\":11.5}",
		"verdict|{\"penaltyType\":\"PRISON\",\"prisonMonths\":36.7}"
	})
	void http_floatOnApi5And9_returns400BeforeLookup(String pathAndBody) throws Exception {
		String[] parts = pathAndBody.split("\\|", 2);
		mockMvc.perform(post("/api/v1/cases/" + MISSING_CASE_ID + "/experience/" + parts[0])
				.contentType(MediaType.APPLICATION_JSON).content(parts[1]))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void http_numericStringStep_passesParsing() throws Exception {
		// 문자열 숫자는 형식 검사를 통과해 다음 단계(사건 조회)까지 간다
		mockMvc.perform(post("/api/v1/cases/" + MISSING_CASE_ID + "/experience/review-steps")
				.contentType(MediaType.APPLICATION_JSON).content("{\"step\":\"3\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
	}
}
