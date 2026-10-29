package com.team2.project.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.common.security.token.AccessTokenVerifier;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenException;
import com.team2.project.support.ApiIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/** 내 회원 정보 GET /members/me (API 명세 M-8, BE-139) */
class MemberMeApiTest extends ApiIntegrationTest {

	private static final String URL = "/api/v1/members/me";

	/** 시험용 토큰: "member-{회원 ID}" → 그 회원으로 인증 */
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

	private final List<Long> memberIds = new ArrayList<>();

	@AfterEach
	void deleteMembers() {
		memberIds.forEach(id -> {
			jdbcTemplate.update("DELETE FROM member_password WHERE member_id = ?", id);
			jdbcTemplate.update("DELETE FROM member WHERE id = ?", id);
		});
		memberIds.clear();
	}

	private long newMember(String provider, String nickname) {
		Long id = jdbcTemplate.queryForObject(
			"INSERT INTO member (provider, provider_user_id, nickname, last_login_at) VALUES (?, ?, ?, now()) RETURNING id",
			Long.class, provider, UUID.randomUUID().toString(), nickname);
		memberIds.add(id);
		return id;
	}

	private ResultActions me(Long memberId) throws Exception {
		var request = get(URL);
		return mockMvc.perform(memberId == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer member-" + memberId));
	}

	@Test
	@DisplayName("카카오 회원은 닉네임이 없으면 표시 이름이 '방청객 {회원 ID}'이고 아이디는 없다")
	void kakaoMember_withoutNickname() throws Exception {
		long id = newMember("KAKAO", null);
		me(id).andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").isEmpty())
			.andExpect(jsonPath("$.displayName").value("방청객 " + id))
			.andExpect(jsonPath("$.provider").value("KAKAO"))
			.andExpect(jsonPath("$.loginId").isEmpty())
			.andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
			.andExpect(jsonPath("$.lastLoginAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
	}

	@Test
	@DisplayName("아이디 회원은 닉네임 · 로그인 아이디가 그대로 보이고, 닉네임을 정하면 카카오 회원도 그 이름이 표시 이름이다")
	void localMember_andNicknameSet() throws Exception {
		String nickname = "me" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		long local = newMember("LOCAL", nickname);
		jdbcTemplate.update("INSERT INTO member_password (member_id, login_id, password_hash) VALUES (?, ?, 'x')", local,
			"t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
		String loginId = jdbcTemplate.queryForObject("SELECT login_id FROM member_password WHERE member_id = ?", String.class,
			local);
		me(local).andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value(nickname))
			.andExpect(jsonPath("$.displayName").value(nickname))
			.andExpect(jsonPath("$.provider").value("LOCAL"))
			.andExpect(jsonPath("$.loginId").value(loginId));

		String kakaoNickname = "mk" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		long kakao = newMember("KAKAO", kakaoNickname);
		me(kakao).andExpect(jsonPath("$.displayName").value(kakaoNickname));
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member_password WHERE member_id = ?", Integer.class,
			kakao)).isZero();
	}

	@Test
	@DisplayName("로그인하지 않으면 401 UNAUTHORIZED")
	void requiresLogin() throws Exception {
		me(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
}
