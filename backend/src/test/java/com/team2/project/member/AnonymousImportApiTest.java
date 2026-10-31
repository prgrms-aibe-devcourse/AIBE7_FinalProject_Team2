package com.team2.project.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.support.ApiIntegrationTest;
import com.team2.project.support.FakeMemberTokens;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 첫 로그인 가져오기 M-9 ~ M-11 (BE-140 3c) */
@Import(FakeMemberTokens.class)
class AnonymousImportApiTest extends ApiIntegrationTest {

	private static final String URL = "/api/v1/members/me/anonymous-import";
	private static final String DISCARD_URL = URL + "/discard";

	private final List<Long> memberIds = new ArrayList<>();
	private final List<UUID> anonymousIds = new ArrayList<>();

	@AfterEach
	void deleteRows() {
		memberIds.forEach(id -> jdbcTemplate.update("DELETE FROM member_anonymous_import WHERE member_id = ?", id));
		String experiences = "(SELECT id FROM experience WHERE member_id = ANY(?) OR anonymous_user_id = ANY(?))";
		Object[] owners = {memberIds.toArray(Long[]::new), anonymousIds.toArray(UUID[]::new)};
		jdbcTemplate.update("DELETE FROM judgment WHERE experience_id IN " + experiences, owners);
		jdbcTemplate.update("DELETE FROM experience_personality WHERE experience_id IN " + experiences, owners);
		jdbcTemplate.update("DELETE FROM experience WHERE member_id = ANY(?) OR anonymous_user_id = ANY(?)", owners);
		anonymousIds.forEach(id -> jdbcTemplate.update("DELETE FROM anonymous_user WHERE id = ?", id));
		memberIds.forEach(id -> jdbcTemplate.update("DELETE FROM member WHERE id = ?", id));
	}

	private long newMember() {
		Long id = jdbcTemplate.queryForObject(
			"INSERT INTO member (provider, provider_user_id) VALUES ('KAKAO', ?) RETURNING id", Long.class, UUID.randomUUID().toString());
		memberIds.add(id);
		return id;
	}

	/** 이 회원에 연결된 익명 ID(로그인이 연결해 둔 상태). memberId가 null이면 연결 안 된 손님 */
	private UUID newAnonymous(Long memberId) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO anonymous_user (id, member_id) VALUES (?, ?)", id, memberId);
		anonymousIds.add(id);
		return id;
	}

	private long anonymousExperience(UUID anonymousId, String status) {
		long caseId = insertCase("MURDER", "PUBLISHED");
		return jdbcTemplate.queryForObject(
			"INSERT INTO experience (anonymous_user_id, case_id, attempt_no, status) VALUES (?, ?, 1, ?) RETURNING id",
			Long.class, anonymousId, caseId, status);
	}

	private void memberExperience(long memberId) {
		jdbcTemplate.update("INSERT INTO experience (member_id, case_id, attempt_no, status) VALUES (?, ?, 1, 'STARTED')",
			memberId, insertCase("MURDER", "PUBLISHED"));
	}

	private void preJudgment(long experienceId) {
		jdbcTemplate.update("""
			INSERT INTO judgment (case_id, subject_type, timing, experience_id, range_option_id, is_published)
			SELECT case_id, 'USER', 'PRE', id, ?, true FROM experience WHERE id = ?""", rangeOptionIds("MURDER").get(3), experienceId);
	}

	private Integer experiences(String ownerColumn, Object owner) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM experience WHERE " + ownerColumn + " = ?", Integer.class, owner);
	}

	private String decision(long memberId) {
		return jdbcTemplate.queryForList("SELECT decision FROM member_anonymous_import WHERE member_id = ?", String.class, memberId)
			.stream().findFirst().orElse(null);
	}

	private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, long memberId, UUID cookie) {
		return request.header(HttpHeaders.AUTHORIZATION, FakeMemberTokens.bearer(memberId))
			.cookie(new Cookie(AnonymousIdCookie.NAME, cookie.toString()));
	}

	@Test
	@DisplayName("M-9: 연결된 익명 ID의 체험 수 · 첫인상 엽서 수를 돌려준다")
	void candidate_counts() throws Exception {
		long memberId = newMember();
		UUID anonymousId = newAnonymous(memberId);
		preJudgment(anonymousExperience(anonymousId, "PRE_JUDGED"));
		anonymousExperience(anonymousId, "STARTED");

		mockMvc.perform(as(get(URL), memberId, anonymousId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.experienceCount").value(2))
			.andExpect(jsonPath("$.preJudgmentCount").value(1));
	}

	@Test
	@DisplayName("M-9: 쿠키 없음 · 남의 익명 ID · 연결 안 된 익명 ID · 체험 없음 · 회원 체험 있음이면 404")
	void candidate_none() throws Exception {
		long memberId = newMember();
		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, FakeMemberTokens.bearer(memberId)))
			.andExpect(status().isNotFound());

		UUID others = newAnonymous(newMember());
		anonymousExperience(others, "STARTED");
		mockMvc.perform(as(get(URL), memberId, others)).andExpect(status().isNotFound());

		UUID unlinked = newAnonymous(null);
		anonymousExperience(unlinked, "STARTED");
		mockMvc.perform(as(get(URL), memberId, unlinked)).andExpect(status().isNotFound());

		UUID empty = newAnonymous(memberId);
		mockMvc.perform(as(get(URL), memberId, empty)).andExpect(status().isNotFound());

		UUID linked = newAnonymous(memberId);
		anonymousExperience(linked, "STARTED");
		memberExperience(memberId);
		mockMvc.perform(as(get(URL), memberId, linked)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("M-9 ~ M-11: 로그인 전이면 401")
	void guest_unauthorized() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(DISCARD_URL)).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("M-10: 진행 중 체험까지 회원 것으로 옮기고 첫인상 엽서는 따라온다. 다시 보내도 같은 답이고 M-9는 404다")
	void import_moves() throws Exception {
		long memberId = newMember();
		UUID anonymousId = newAnonymous(memberId);
		long experienceId = anonymousExperience(anonymousId, "PRE_JUDGED");
		preJudgment(experienceId);
		anonymousExperience(anonymousId, "STARTED");

		mockMvc.perform(as(post(URL), memberId, anonymousId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.experienceCount").value(2));

		assertThat(experiences("anonymous_user_id", anonymousId)).isZero();
		assertThat(experiences("member_id", memberId)).isEqualTo(2);
		assertThat(decision(memberId)).isEqualTo("IMPORTED");
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM judgment WHERE experience_id = ?", Integer.class, experienceId)).isEqualTo(1);

		mockMvc.perform(as(post(URL), memberId, anonymousId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.experienceCount").value(2));
		mockMvc.perform(as(get(URL), memberId, anonymousId)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("M-10: 동시에 두 번 보내도 한 번만 옮기고 둘 다 같은 수를 받는다")
	void import_concurrent() throws Exception {
		long memberId = newMember();
		UUID anonymousId = newAnonymous(memberId);
		anonymousExperience(anonymousId, "STARTED");
		Callable<String> call = () -> mockMvc.perform(as(post(URL), memberId, anonymousId))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			for (Future<String> result : pool.invokeAll(List.of(call, call))) {
				assertThat(result.get()).contains("\"experienceCount\":1");
			}
		} finally {
			pool.shutdown();
		}
		assertThat(experiences("member_id", memberId)).isEqualTo(1);
	}

	@Test
	@DisplayName("M-10: 후보가 아니면(다른 회원의 익명 ID) 409이고 옮기지 않는다")
	void import_notCandidate() throws Exception {
		long memberId = newMember();
		UUID others = newAnonymous(newMember());
		anonymousExperience(others, "STARTED");

		mockMvc.perform(as(post(URL), memberId, others))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ANONYMOUS_IMPORT_NOT_AVAILABLE"));

		assertThat(experiences("anonymous_user_id", others)).isEqualTo(1);
		assertThat(decision(memberId)).isNull();
	}

	@Test
	@DisplayName("M-11: 결정만 남기고 익명 기록은 그대로 두며 쿠키를 지운다. 다시 보내도 204, 이후 M-9는 404, M-10은 409다")
	void discard_keepsRows() throws Exception {
		long memberId = newMember();
		UUID anonymousId = newAnonymous(memberId);
		anonymousExperience(anonymousId, "STARTED");

		mockMvc.perform(as(post(DISCARD_URL), memberId, anonymousId))
			.andExpect(status().isNoContent())
			.andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));

		assertThat(experiences("anonymous_user_id", anonymousId)).isEqualTo(1);
		assertThat(experiences("member_id", memberId)).isZero();
		assertThat(decision(memberId)).isEqualTo("DISCARDED");

		mockMvc.perform(as(post(DISCARD_URL), memberId, anonymousId)).andExpect(status().isNoContent());
		mockMvc.perform(as(get(URL), memberId, anonymousId)).andExpect(status().isNotFound());
		mockMvc.perform(as(post(URL), memberId, anonymousId)).andExpect(status().isConflict());
	}

	@Test
	@DisplayName("M-11: 후보가 아니면 409이고 쿠키를 지우지 않는다")
	void discard_notCandidate() throws Exception {
		long memberId = newMember();
		UUID empty = newAnonymous(memberId);

		mockMvc.perform(as(post(DISCARD_URL), memberId, empty))
			.andExpect(status().isConflict())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
		assertThat(decision(memberId)).isNull();
	}
}
