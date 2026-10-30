package com.team2.project.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.common.security.token.AccessTokenVerifier;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenException;
import com.team2.project.community.service.CommunityHomeCache;
import com.team2.project.support.ApiIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 커뮤니티 메인(C-6) 응답 캐시 — 기본 설정(1시간)으로 본다 (BE-115 리뷰 반영) */
class CommunityHomeCacheTest extends ApiIntegrationTest {

	private static final String HOME = "/api/v1/community/home";

	@TestConfiguration
	static class TestBeans {

		@Bean
		@Primary
		AccessTokenVerifier fakeAccessTokenVerifier() {
			return token -> {
				if (token.startsWith("member-")) {
					return MemberPrincipal.user(Long.parseLong(token.substring("member-".length())));
				}
				throw TokenException.invalid("알 수 없는 토큰");
			};
		}
	}

	@Autowired
	private CommunityHomeCache homeCache;

	private Long memberId;
	private UUID anonymousId;

	@AfterEach
	void cleanUp() {
		homeCache.clear();	// 다른 시험이 이 시험의 캐시를 보지 않게
		if (memberId != null) {
			jdbcTemplate.update("DELETE FROM community_comment WHERE member_id = ?", memberId);
			jdbcTemplate.update("DELETE FROM community_post WHERE member_id = ?", memberId);
			jdbcTemplate.update("DELETE FROM xp_event WHERE anonymous_user_id = ?", anonymousId);
			jdbcTemplate.update("DELETE FROM attendance WHERE anonymous_user_id = ?", anonymousId);
			jdbcTemplate.update("DELETE FROM anonymous_user WHERE id = ?", anonymousId);
			jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
		}
	}

	private String home() throws Exception {
		return mockMvc.perform(get(HOME)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	/** 사건 글을 하나 쓴다 — 오늘 이야기가 생겨 메인의 "이야기 많은 사건"에 오르는 글 */
	private long writeCasePost() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		memberId = jdbcTemplate.queryForObject(
			"INSERT INTO member (provider, provider_user_id, nickname) VALUES ('LOCAL', ?, ?) RETURNING id", Long.class,
			UUID.randomUUID().toString(), "ca" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
		anonymousId = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO anonymous_user (id, member_id) VALUES (?, ?)", anonymousId, memberId);
		mockMvc.perform(post("/api/v1/community/posts").header(HttpHeaders.AUTHORIZATION, "Bearer member-" + memberId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"FREE\",\"caseId\":" + caseId + ",\"title\":\"캐시 확인\",\"body\":\"본문\"}"))
			.andExpect(status().isCreated());
		return caseId;
	}

	@Test
	@DisplayName("메인은 계산한 응답을 일정 시간 그대로 준다 — 그 사이 새 글이 와도 반영되지 않고, 캐시를 비우면 다시 계산한다")
	void home_servedFromCache_untilCleared() throws Exception {
		homeCache.clear();
		String before = home();
		writeCasePost();

		assertThat(home()).isEqualTo(before);	// 캐시된 응답 — 새 글이 보이지 않는다

		homeCache.clear();
		String after = home();
		assertThat(after).isNotEqualTo(before);
		assertThat(after).contains("\"talkCount\":1");	// 새로 계산해 오늘의 이야기가 반영됐다
		assertThat(home()).isEqualTo(after);	// 다시 캐시된다
	}
}
