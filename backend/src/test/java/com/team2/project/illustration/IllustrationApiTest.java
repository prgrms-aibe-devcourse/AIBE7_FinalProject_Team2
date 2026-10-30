package com.team2.project.illustration;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.team2.project.illustration.service.IllustrationCatalog;
import com.team2.project.support.ApiIntegrationTest;

/** 일러스트 총 개수 GET /illustrations/summary (API 명세 I-1, BE-142) */
class IllustrationApiTest extends ApiIntegrationTest {

	@Test
	@DisplayName("로그인 · 쿠키 없이 200으로 총 개수와 종류별 개수를 준다. 쿠키는 발급하지 않는다")
	void summary_public() throws Exception {
		int total = IllustrationCatalog.read().size();

		mockMvc.perform(get("/api/v1/illustrations/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(total))
				.andExpect(jsonPath("$.byKind.cases", greaterThan(0)))
				.andExpect(jsonPath("$.byKind['characters/icon/cropped']", greaterThan(0)))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}
}
