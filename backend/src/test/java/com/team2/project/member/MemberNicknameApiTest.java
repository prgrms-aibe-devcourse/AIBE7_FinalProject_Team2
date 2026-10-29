package com.team2.project.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.common.security.token.AccessTokenVerifier;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenException;
import com.team2.project.support.ApiIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 닉네임 설정 PUT /members/me/nickname (API 명세 M-7, BE-139) */
class MemberNicknameApiTest extends ApiIntegrationTest {

	private static final String URL = "/api/v1/members/me/nickname";

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
		memberIds.forEach(id -> jdbcTemplate.update("DELETE FROM member WHERE id = ?", id));
		memberIds.clear();
	}

	/** 시험마다 겹치지 않는 닉네임(영문 소문자 · 숫자 12자) */
	private static String unique() {
		return "nn" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	private long newMember(String provider, String nickname) {
		Long id = jdbcTemplate.queryForObject(
			"INSERT INTO member (provider, provider_user_id, nickname) VALUES (?, ?, ?) RETURNING id",
			Long.class, provider, UUID.randomUUID().toString(), nickname);
		memberIds.add(id);
		return id;
	}

	private ResultActions changeNickname(Long memberId, String json) throws Exception {
		var request = put(URL).contentType(MediaType.APPLICATION_JSON).content(json);
		return mockMvc.perform(
			memberId == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer member-" + memberId));
	}

	private String nicknameOf(long memberId) {
		return jdbcTemplate.queryForObject("SELECT nickname FROM member WHERE id = ?", String.class, memberId);
	}

	@Test
	@DisplayName("닉네임이 없는 카카오 회원도 닉네임을 정할 수 있다")
	void kakaoMember_setsNickname() throws Exception {
		long id = newMember("KAKAO", null);
		String nickname = unique();
		changeNickname(id, "{\"nickname\":\"" + nickname + "\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value(nickname));
		assertThat(nicknameOf(id)).isEqualTo(nickname);
	}

	@Test
	@DisplayName("아이디 회원이 닉네임을 바꾼다. 내 닉네임의 대소문자만 바꾸는 것도 된다")
	void localMember_changesNickname() throws Exception {
		long id = newMember("LOCAL", unique());
		String changed = unique();
		changeNickname(id, "{\"nickname\":\"" + changed + "\"}").andExpect(status().isOk());
		assertThat(nicknameOf(id)).isEqualTo(changed);
		changeNickname(id, "{\"nickname\":\"" + changed.toUpperCase() + "\"}").andExpect(status().isOk());
		assertThat(nicknameOf(id)).isEqualTo(changed.toUpperCase());
	}

	@Test
	@DisplayName("다른 회원이 쓰는 닉네임이면 409 DUPLICATE_NICKNAME — 대소문자만 달라도 같다. 닉네임은 그대로")
	void duplicate_rejected() throws Exception {
		String taken = unique();
		newMember("LOCAL", taken);
		String mine = unique();
		long id = newMember("LOCAL", mine);
		for (String attempt : List.of(taken, taken.toUpperCase())) {
			changeNickname(id, "{\"nickname\":\"" + attempt + "\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));
		}
		assertThat(nicknameOf(id)).isEqualTo(mine);
	}

	@Test
	@DisplayName("형식이 틀리면 400 VALIDATION_ERROR(details nickname): 없음 · 문자열 아님 · 1자 · 13자 · 공백 · 기호 · 한글 9자 · 자동 이름 · 방청객으로 시작")
	void invalidFormat_rejected() throws Exception {
		long id = newMember("KAKAO", null);
		for (String json : List.of("{}", "{\"nickname\":null}", "{\"nickname\":1}", "{\"nickname\":\"가\"}",
			"{\"nickname\":\"abcdefghijklm\"}", "{\"nickname\":\"닉 네임\"}", "{\"nickname\":\"닉네임!\"}",
			"{\"nickname\":\"가나다라마바사아자\"}", "{\"nickname\":\"방청객 " + id + "\"}",
			"{\"nickname\":\"방청객123\"}", "{\"nickname\":\"방청객\"}")) {	// 자동 이름을 흉내 내는 "방청객" 시작 닉네임도 막는다
			changeNickname(id, json).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("nickname"));
		}
		assertThat(nicknameOf(id)).isNull();
		changeNickname(id, "{\"nickname\":\"가나다라마바사아\"}").andExpect(status().isOk());	// 한글 8자 경계
	}

	@Test
	@DisplayName("로그인하지 않으면 401 UNAUTHORIZED")
	void requiresLogin() throws Exception {
		changeNickname(null, "{\"nickname\":\"" + unique() + "\"}")
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@DisplayName("같은 닉네임을 동시에 정하면 한 명만 되고 나머지는 409")
	void concurrent_onlyOneWins() throws Exception {
		String nickname = unique();
		int threads = 4;
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			ids.add(newMember("KAKAO", null));
		}
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Future<MvcResult>> futures = new ArrayList<>();
			for (Long id : ids) {
				Callable<MvcResult> call = () -> {
					start.await();
					return changeNickname(id, "{\"nickname\":\"" + nickname + "\"}").andReturn();
				};
				futures.add(pool.submit(call));
			}
			start.countDown();
			int ok = 0;
			int conflict = 0;
			for (Future<MvcResult> future : futures) {
				int status = future.get().getResponse().getStatus();
				if (status == 200) {
					ok++;
				} else if (status == 409) {
					conflict++;
				}
			}
			assertThat(ok).isEqualTo(1);
			assertThat(conflict).isEqualTo(threads - 1);
		} finally {
			pool.shutdownNow();
		}
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member WHERE nickname = ?", Integer.class, nickname))
			.isEqualTo(1);
	}
}
