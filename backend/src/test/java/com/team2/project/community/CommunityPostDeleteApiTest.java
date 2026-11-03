package com.team2.project.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.level.domain.LevelCurve;
import com.team2.project.support.ApiIntegrationTest;
import com.team2.project.support.FakeMemberTokens;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 커뮤니티 글 삭제 C-8 · 관리자 글 삭제 A-15 (BE-167). 댓글 · 좋아요 함께 삭제, 경험치 회수, 레벨 유지 */
@Import(FakeMemberTokens.class)
@TestPropertySource(properties = "community.home-cache-ttl=0")
class CommunityPostDeleteApiTest extends ApiIntegrationTest {

	private static final String POSTS = "/api/v1/community/posts";
	private static final String ADMIN_PASSWORD = "correct-horse-battery";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";
	private static final String CSRF_COOKIE = "XSRF-TOKEN";

	@Autowired AdminAccountRepository accountRepository;
	@Autowired PasswordEncoder passwordEncoder;

	private record User(long memberId, UUID anonymousId) {

		MockHttpServletRequestBuilder sign(MockHttpServletRequestBuilder request) {
			return request.header(HttpHeaders.AUTHORIZATION, ApiIntegrationTest.bearer(memberId))
				.cookie(new Cookie(COOKIE, anonymousId.toString()));
		}
	}

	private final List<Long> memberIds = new ArrayList<>();
	private long caseId;
	private AdminAccount admin;

	@BeforeEach
	void setUp() {
		caseId = insertCase("MURDER", "PUBLISHED");
		admin = accountRepository.save(AdminAccount.withPassword("del-" + UUID.randomUUID() + "@example.com",
			passwordEncoder.encode(ADMIN_PASSWORD), "삭제 테스트"));
	}

	@AfterEach
	void cleanUp() {
		for (Long memberId : memberIds) {
			jdbcTemplate.update("DELETE FROM community_like WHERE post_id IN (SELECT id FROM community_post WHERE member_id = ?)", memberId);
			jdbcTemplate.update("DELETE FROM community_comment WHERE post_id IN (SELECT id FROM community_post WHERE member_id = ?)", memberId);
			jdbcTemplate.update("DELETE FROM community_comment WHERE member_id = ?", memberId);
			jdbcTemplate.update("DELETE FROM community_like WHERE member_id = ?", memberId);
			jdbcTemplate.update("DELETE FROM community_post WHERE member_id = ?", memberId);
			jdbcTemplate.update("DELETE FROM anonymous_user WHERE member_id = ?", memberId);
		}
		memberIds.clear();
		jdbcTemplate.update("DELETE FROM admin_audit_log WHERE actor_email = ?", admin.getEmail());
		accountRepository.deleteById(admin.getId());
	}

	private User newUser() {
		long memberId = registerMember();
		memberIds.add(memberId);
		UUID anonymousId = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO anonymous_user (id, member_id) VALUES (?, ?)", anonymousId, memberId);
		return new User(memberId, anonymousId);
	}

	private long writePost(User user, String title) throws Exception {
		String body = mockMvc.perform(user.sign(post(POSTS)).contentType(MediaType.APPLICATION_JSON).content("""
				{"category":"FREE","caseId":%d,"title":"%s","body":"본문"}""".formatted(caseId, title)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.postId")).longValue();
	}

	private ResultActions deleteMine(User user, long postId) throws Exception {
		return mockMvc.perform(user == null ? delete(POSTS + "/" + postId) : user.sign(delete(POSTS + "/" + postId)));
	}

	private int xp(User user, String source) {
		return jdbcTemplate.queryForObject(
			"SELECT coalesce(sum(amount), 0) FROM member_xp_event WHERE member_id = ? AND source = ?",
			Integer.class, user.memberId(), source);
	}

	private int totalXp(User user) {
		return jdbcTemplate.queryForObject("SELECT coalesce(sum(amount), 0) FROM member_xp_event WHERE member_id = ?",
			Integer.class, user.memberId());
	}

	private int postRows(long postId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM community_post WHERE id = ?", Integer.class, postId);
	}

	private int count(String table, long postId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE post_id = ?", Integer.class, postId);
	}

	@Test
	@DisplayName("C-8: 본인 글을 지우면 204, 글 · 댓글 · 좋아요가 함께 사라지고 상세는 404")
	void deleteMine_removesPostCommentsAndLikes() throws Exception {
		User author = newUser();
		User other = newUser();
		long postId = writePost(author, "지울 글");
		mockMvc.perform(other.sign(post(POSTS + "/" + postId + "/like"))).andExpect(status().isOk());
		mockMvc.perform(other.sign(post(POSTS + "/" + postId + "/comments")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\":\"댓글\"}")).andExpect(status().isCreated());

		deleteMine(author, postId).andExpect(status().isNoContent());

		assertThat(postRows(postId)).isZero();
		assertThat(count("community_comment", postId)).isZero();
		assertThat(count("community_like", postId)).isZero();
		mockMvc.perform(get(POSTS + "/" + postId)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("C-8: 남의 글은 403으로 지워지지 않고, 없는 글은 404, 로그인 전은 401")
	void deleteMine_guards() throws Exception {
		User author = newUser();
		User other = newUser();
		long postId = writePost(author, "남의 글");

		deleteMine(other, postId).andExpect(status().isForbidden());
		deleteMine(author, postId + 100_000).andExpect(status().isNotFound());
		deleteMine(null, postId).andExpect(status().isUnauthorized());

		mockMvc.perform(get(POSTS + "/" + postId)).andExpect(status().isOk());
	}

	@Test
	@DisplayName("C-8: 글이 준 경험치(작성 · 받은 좋아요)는 거두고, 레벨이 내려갈 만큼이면 레벨 시작 XP까지 채운다")
	void deleteMine_revokesXpButKeepsLevel() throws Exception {
		User author = newUser();
		User fan = newUser();
		long first = writePost(author, "첫 글");
		long second = writePost(author, "둘째 글");
		mockMvc.perform(fan.sign(post(POSTS + "/" + second + "/like"))).andExpect(status().isOk());
		assertThat(xp(author, "POST")).isEqualTo(60);
		assertThat(xp(author, "LIKE_RECEIVED")).isEqualTo(3);
		assertThat(totalXp(author)).isEqualTo(63);	// Lv2 시작(41)을 넘은 상태

		deleteMine(author, second).andExpect(status().isNoContent());

		assertThat(xp(author, "POST")).isEqualTo(30);	// 지운 글의 작성 XP만 거둔다
		assertThat(xp(author, "LIKE_RECEIVED")).isZero();
		assertThat(totalXp(author)).isEqualTo(LevelCurve.xpAt(2));	// 30 + 보정 = Lv2 시작 XP
		assertThat(xp(author, "LEVEL_FLOOR")).isEqualTo(LevelCurve.xpAt(2) - 30);
		assertThat(xp(fan, "LIKE_GIVEN")).isEqualTo(2);	// 누른 사람의 XP는 그대로
		assertThat(postRows(first)).isEqualTo(1);	// 다른 글은 그대로
	}

	@Test
	@DisplayName("A-15: 관리자는 남의 글도 지울 수 있고(204) 감사 로그에 글 ID만 남는다. 로그인 없이는 거절")
	void adminDelete() throws Exception {
		User author = newUser();
		long postId = writePost(author, "관리자가 지울 글");
		String url = "/api/v1/admin/community/posts/" + postId;

		mockMvc.perform(delete(url)).andExpect(status().isForbidden());	// 관리자 체인은 상태 변경 요청의 CSRF를 먼저 본다

		Cookie first = mockMvc.perform(get("/api/v1/admin/auth/csrf")).andReturn().getResponse().getCookie(CSRF_COOKIE);
		MvcResult login = mockMvc.perform(post("/api/v1/admin/auth/login").cookie(first)
				.header(CSRF_HEADER, first.getValue()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(admin.getEmail(), ADMIN_PASSWORD)))
			.andExpect(status().isOk()).andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		Cookie csrf = login.getResponse().getCookie(CSRF_COOKIE);

		mockMvc.perform(delete(url).session(session).cookie(csrf).header(CSRF_HEADER, csrf.getValue()))
			.andExpect(status().isNoContent());
		mockMvc.perform(delete(url).session(session).cookie(csrf).header(CSRF_HEADER, csrf.getValue()))
			.andExpect(status().isNotFound());

		mockMvc.perform(get(POSTS + "/" + postId)).andExpect(status().isNotFound());
		assertThat(xp(author, "POST")).isZero();
		assertThat(jdbcTemplate.queryForObject("""
			SELECT detail::text FROM admin_audit_log WHERE actor_email = ? AND action = 'COMMUNITY_POST_DELETE'
			AND target_id = ?""", String.class, admin.getEmail(), postId)).contains("authorMemberId").doesNotContain("관리자가");
	}
}
