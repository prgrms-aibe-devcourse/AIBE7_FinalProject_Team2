package com.team2.project.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * 토큰 검증기(AccessTokenVerifier, WBS 7.4.3)가 아직 없을 때 (BE-57).
 * 토큰 없는 요청은 지금처럼 동작하고, Bearer 토큰이 붙은 요청은 익명으로 바꾸지 않고 401로 거절한다.
 */
class UserChainWithoutVerifierTest extends ApiIntegrationTest {

	@Test
	void noToken_works() throws Exception {
		mockMvc.perform(get("/api/v1/cases")).andExpect(status().isOk());
	}

	@Test
	void bearerToken_rejectedAsInvalid() throws Exception {
		mockMvc.perform(get("/api/v1/cases").header(HttpHeaders.AUTHORIZATION, "Bearer anything"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}
}
