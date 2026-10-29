package com.team2.project.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 실제 애플리케이션(보안 필터 · 정적 리소스 처리 포함)에서 요청 자체가 맞지 않을 때의 에러 코드 (BE-132).
 * 운영 점검(2026-10-29)에서 세 경우 모두 VALIDATION_ERROR로 내려오던 것을 재현한 요청들이다
 */
class RequestErrorCodeApiTest extends ApiIntegrationTest {

	@Test
	@DisplayName("없는 API 주소는 404 NOT_FOUND")
	void unknownPath() throws Exception {
		mockMvc.perform(get("/api/v1/nope"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.message").value("요청한 주소를 찾을 수 없습니다."));
	}

	@Test
	@DisplayName("지원하지 않는 메서드는 405 METHOD_NOT_ALLOWED + Allow")
	void methodNotAllowed() throws Exception {
		mockMvc.perform(get("/api/v1/profile-settings"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
			.andExpect(header().string("Allow", containsString("PATCH")));
	}

	@Test
	@DisplayName("지원하지 않는 Content-Type은 415 UNSUPPORTED_MEDIA_TYPE")
	void unsupportedMediaType() throws Exception {
		mockMvc.perform(post("/api/v1/personality/results").contentType(MediaType.TEXT_PLAIN).content("x"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	@DisplayName("입력값 오류는 그대로 400 VALIDATION_ERROR, 도메인 404(CASE_NOT_FOUND)도 그대로")
	void unchanged() throws Exception {
		mockMvc.perform(get("/api/v1/cases/abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get("/api/v1/cases/999999999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
	}
}
