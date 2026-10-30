package com.team2.project.experience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.support.ApiIntegrationTest;
import com.team2.project.support.FakeMemberTokens;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 체험 · 성향 테스트 결과의 주인: 회원이면 회원, 손님이면 익명 ID (BE-140 3a) */
@Import(FakeMemberTokens.class)
class MemberOwnedExperienceApiTest extends ApiIntegrationTest {

	private final List<Long> memberIds = new ArrayList<>();
	private final List<UUID> anonymousIds = new ArrayList<>();

	private long newMember() {
		Long id = jdbcTemplate.queryForObject(
			"INSERT INTO member (provider, provider_user_id) VALUES ('KAKAO', ?) RETURNING id", Long.class, UUID.randomUUID().toString());
		memberIds.add(id);
		return id;
	}

	@AfterEach
	void deleteMembers() {
		memberIds.forEach(id -> {
			jdbcTemplate.update("DELETE FROM personality_result WHERE member_id = ?", id);
			jdbcTemplate.update("DELETE FROM experience_personality WHERE experience_id IN (SELECT id FROM experience WHERE member_id = ?)", id);
			jdbcTemplate.update("DELETE FROM experience WHERE member_id = ?", id);
			jdbcTemplate.update("DELETE FROM member WHERE id = ?", id);
		});
		anonymousIds.forEach(id -> {
			jdbcTemplate.update("DELETE FROM experience WHERE anonymous_user_id = ?", id);
			jdbcTemplate.update("DELETE FROM personality_result WHERE anonymous_user_id = ?", id);
			jdbcTemplate.update("DELETE FROM attendance WHERE anonymous_user_id = ?", id);
			jdbcTemplate.update("DELETE FROM anonymous_user WHERE id = ?", id);
		});
	}

	private List<Map<String, Object>> experienceRows(long caseId) {
		return jdbcTemplate.queryForList("SELECT anonymous_user_id, member_id FROM experience WHERE case_id = ?", caseId);
	}

	private String answers() {
		return jdbcTemplate.queryForList("SELECT id FROM personality_question WHERE is_active ORDER BY display_order", Long.class).stream()
			.map(id -> "{\"questionId\":" + id + ",\"value\":2}")
			.collect(Collectors.joining(",", "{\"answers\":[", "]}"));
	}

	@Test
	@DisplayName("회원이 체험을 시작하면 회원이 주인이고 익명 ID 쿠키를 발급하지 않는다. 다시 시작하면 같은 체험이다")
	void member_start_ownedByMember_noCookie() throws Exception {
		long memberId = newMember();
		long caseId = insertCase("MURDER", "PUBLISHED");

		mockMvc.perform(post(experienceUrl(caseId)).header(HttpHeaders.AUTHORIZATION, FakeMemberTokens.bearer(memberId)))
			.andExpect(status().isCreated())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
		mockMvc.perform(post(experienceUrl(caseId)).header(HttpHeaders.AUTHORIZATION, FakeMemberTokens.bearer(memberId)))
			.andExpect(status().isOk());

		assertThat(experienceRows(caseId)).hasSize(1).first().satisfies(row -> {
			assertThat(row.get("member_id")).isEqualTo(memberId);
			assertThat(row.get("anonymous_user_id")).isNull();
		});
	}

	@Test
	@DisplayName("쿠키가 있어도 로그인했으면 회원이 주인이다 — 같은 브라우저의 손님 체험과 섞이지 않는다")
	void member_withCookie_separateFromGuestExperience() throws Exception {
		long memberId = newMember();
		long caseId = insertCase("MURDER", "PUBLISHED");
		Cookie guest = startAndGetCookie(caseId);
		anonymousIds.add(UUID.fromString(guest.getValue()));
		jdbcTemplate.update("UPDATE experience SET status = 'PRE_JUDGED', last_reviewed_step = 1 WHERE case_id = ?", caseId);

		mockMvc.perform(post(experienceUrl(caseId)).cookie(guest).header(HttpHeaders.AUTHORIZATION, FakeMemberTokens.bearer(memberId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("STARTED"));

		assertThat(experienceRows(caseId)).hasSize(2);
		mockMvc.perform(get(experienceUrl(caseId)).cookie(guest).header(HttpHeaders.AUTHORIZATION, FakeMemberTokens.bearer(memberId)))
			.andExpect(jsonPath("$.status").value("STARTED"));
		mockMvc.perform(get(experienceUrl(caseId)).cookie(guest))
			.andExpect(jsonPath("$.status").value("PRE_JUDGED"));
	}

	@Test
	@DisplayName("다른 회원의 체험은 내 체험이 아니다 (API 3 · 4는 404)")
	void member_cannotSeeOtherMembersExperience() throws Exception {
		long owner = newMember();
		long stranger = newMember();
		long caseId = insertCase("MURDER", "PUBLISHED");
		mockMvc.perform(post(experienceUrl(caseId)).header(HttpHeaders.AUTHORIZATION, FakeMemberTokens.bearer(owner)))
			.andExpect(status().isCreated());

		mockMvc.perform(get(experienceUrl(caseId)).header(HttpHeaders.AUTHORIZATION, FakeMemberTokens.bearer(stranger)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("EXPERIENCE_NOT_FOUND"));
		mockMvc.perform(get(experienceUrl(caseId) + "/overview").header(HttpHeaders.AUTHORIZATION, FakeMemberTokens.bearer(owner)))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("사건 목록 · 상세의 내 진행 상태도 회원 체험 기준이다")
	void caseList_myStatus_ofMember() throws Exception {
		long memberId = newMember();
		long caseId = insertCase("MURDER", "PUBLISHED");
		mockMvc.perform(post(experienceUrl(caseId)).header(HttpHeaders.AUTHORIZATION, FakeMemberTokens.bearer(memberId)))
			.andExpect(status().isCreated());
		String itemStatus = "$.cases[?(@.caseId == %d)].myStatus".formatted(caseId);

		mockMvc.perform(get("/api/v1/cases").header(HttpHeaders.AUTHORIZATION, FakeMemberTokens.bearer(memberId)))
			.andExpect(jsonPath(itemStatus).value("IN_PROGRESS"));
		mockMvc.perform(get("/api/v1/cases/" + caseId).header(HttpHeaders.AUTHORIZATION, FakeMemberTokens.bearer(memberId)))
			.andExpect(jsonPath("$.myStatus").value("IN_PROGRESS"));
		mockMvc.perform(get("/api/v1/cases/" + caseId))	// 손님(쿠키 없음)은 없다
			.andExpect(jsonPath("$.myStatus").doesNotExist());
	}

	@Test
	@DisplayName("회원의 성향 테스트: 쿠키를 발급하지 않고 회원이 주인이다. 재응시는 현재 결과를 바꾸고, 재심은 한 번뿐이다")
	void member_personality_ownedByMember() throws Exception {
		long memberId = newMember();
		String token = FakeMemberTokens.bearer(memberId);
		String answers = answers();

		mockMvc.perform(post("/api/v1/personality/results").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON).content(answers))
			.andExpect(status().isCreated())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
		mockMvc.perform(post("/api/v1/personality/results").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON).content(answers))
			.andExpect(status().isCreated());

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM personality_result WHERE member_id = ?", Integer.class, memberId)).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM personality_result WHERE member_id = ? AND is_current", Integer.class, memberId)).isEqualTo(1);
		mockMvc.perform(get("/api/v1/personality/results/me").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());

		String baseline = answers.substring(0, answers.length() - 1) + ",\"baseline\":true}";
		mockMvc.perform(post("/api/v1/personality/results").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON).content(baseline))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/personality/results").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON).content(baseline))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BASELINE_RETAKE_USED"));
	}

	@Test
	@DisplayName("손님의 성향 결과는 같은 브라우저로 로그인한 회원의 결과가 아니다 — 가져오지 않는다")
	void guestPersonality_notVisibleToMember() throws Exception {
		long memberId = newMember();
		String cookie = mockMvc.perform(post("/api/v1/personality/results")
				.contentType(MediaType.APPLICATION_JSON).content(answers()))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE).split(";")[0].substring((COOKIE + "=").length());
		anonymousIds.add(UUID.fromString(cookie));

		mockMvc.perform(get("/api/v1/personality/results/me").cookie(new Cookie(COOKIE, cookie))
				.header(HttpHeaders.AUTHORIZATION, FakeMemberTokens.bearer(memberId)))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/personality/results/me").cookie(new Cookie(COOKIE, cookie))).andExpect(status().isOk());
	}

	@Test
	@DisplayName("마이페이지의 체험 목록 · 성향은 회원 기준이다(꾸미기 등은 3b까지 쿠키 기준)")
	void myPage_experiencesAndPersonality_ofMember() throws Exception {
		long memberId = newMember();
		long caseId = insertCase("MURDER", "PUBLISHED");
		long guestCaseId = insertCase("FRAUD", "PUBLISHED");
		Cookie guest = startAndGetCookie(guestCaseId);
		anonymousIds.add(UUID.fromString(guest.getValue()));
		mockMvc.perform(post(experienceUrl(caseId)).header(HttpHeaders.AUTHORIZATION, FakeMemberTokens.bearer(memberId)))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/my-page").cookie(guest).header(HttpHeaders.AUTHORIZATION, FakeMemberTokens.bearer(memberId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.experiences.length()").value(1))
			.andExpect(jsonPath("$.experiences[0].caseId").value(caseId))
			.andExpect(jsonPath("$.personality").doesNotExist());
		mockMvc.perform(get("/api/v1/my-page").cookie(guest))
			.andExpect(jsonPath("$.experiences[0].caseId").value(guestCaseId));
	}
}
