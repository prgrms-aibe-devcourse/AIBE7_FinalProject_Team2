package com.team2.project.auth.oauth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

/** KAKAO_CLIENT_ID가 없으면 카카오 로그인 경로만 꺼지고 서버는 뜬다 (BE-62). 이 테스트가 뜨는 것 자체가 기동 확인이다 */
class KakaoOAuthDisabledTest extends ApiIntegrationTest {

	@Test
	void 키가_없으면_카카오_로그인_경로는_없다() throws Exception {
		mockMvc.perform(get("/api/v1/auth/oauth2/authorization/kakao")).andExpect(status().isNotFound());
	}
}
