package com.team2.project.level;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.level.domain.XpSource;
import com.team2.project.level.service.LevelService;
import com.team2.project.support.ApiIntegrationTest;
import com.team2.project.support.FakeMemberTokens;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 레벨 · 직업 조회 · 선택 · SNS 공유 보상 · 경험치 지급 상한 (L-1 ~ L-3, BE-106). 로그인한 회원만, 손님은 401 (BE-140) */
@Import(FakeMemberTokens.class)
class LevelApiTest extends ApiIntegrationTest {

	private static final String LEVEL = "/api/v1/level";

	@Autowired
	private LevelService levelService;

	/** 지급 상한과 상관없이 XP만 채워 레벨을 올린다 */
	private void giveXp(long memberId, int amount) {
		jdbcTemplate.update("""
			INSERT INTO member_xp_event (member_id, source, ref_key, amount, awarded_on)
			VALUES (?, 'POST', ?, ?, current_date - 30)
			""", memberId, "seed-" + UUID.randomUUID(), amount);
	}

	private ResultActions selectJob(long memberId, String job) throws Exception {
		return mockMvc.perform(put(LEVEL + "/job").header("Authorization", bearer(memberId))
			.contentType(MediaType.APPLICATION_JSON).content("{\"job\":\"" + job + "\"}"));
	}

	/** 그 회원이 완료한 체험 (회원이 주인) */
	private void insertMemberCompleted(long memberId, long caseId) {
		jdbcTemplate.update("""
			INSERT INTO experience (member_id, case_id, attempt_no, status, completed_at)
			VALUES (?, ?, 1, 'COMPLETED', now())
			""", memberId, caseId);
	}

	@Test
	@DisplayName("L-1: 기록이 없는 회원은 Lv1 · 0 XP · 무직, 직업 11개")
	void get_initial() throws Exception {
		mockMvc.perform(get(LEVEL).header("Authorization", bearer(registerMember())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.level").value(1))
			.andExpect(jsonPath("$.xp").value(0))
			.andExpect(jsonPath("$.nextLevelXp").value(41))
			.andExpect(jsonPath("$.job.code").value("NONE"))
			.andExpect(jsonPath("$.job.name").value("무직"))
			.andExpect(jsonPath("$.jobs.length()").value(11));
	}

	@Test
	@DisplayName("L-1: 손님(토큰 없음)은 401 UNAUTHORIZED, 쿠키는 무시하고 Set-Cookie도 없다")
	void get_guest() throws Exception {
		mockMvc.perform(get(LEVEL))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		var response = mockMvc.perform(get(LEVEL).cookie(new Cookie(COOKIE, UUID.randomUUID().toString())))
			.andExpect(status().isUnauthorized()).andReturn().getResponse();
		assertThat(response.getHeader("Set-Cookie")).isNull();
	}

	@Test
	@DisplayName("L-1: 원장 XP가 쌓이면 레벨이 오르고 직업이 열린다 (385 → Lv10 로스쿨생)")
	void get_afterXp() throws Exception {
		long member = registerMember();
		giveXp(member, 385);
		mockMvc.perform(get(LEVEL).header("Authorization", bearer(member)))
			.andExpect(jsonPath("$.level").value(10))
			.andExpect(jsonPath("$.xp").value(385))
			.andExpect(jsonPath("$.currentLevelXp").value(385))
			.andExpect(jsonPath("$.job.code").value("LAW_STUDENT"))
			.andExpect(jsonPath("$.jobs[1].unlocked").value(true))
			.andExpect(jsonPath("$.jobs[2].unlocked").value(false));
	}

	@Test
	@DisplayName("L-1: 다른 회원의 XP는 내 레벨에 섞이지 않는다")
	void get_otherMemberXpNotMixed() throws Exception {
		long rich = registerMember();
		long other = registerMember();
		giveXp(rich, 385);
		mockMvc.perform(get(LEVEL).header("Authorization", bearer(other)))
			.andExpect(jsonPath("$.level").value(1))
			.andExpect(jsonPath("$.xp").value(0));
		mockMvc.perform(get(LEVEL).header("Authorization", bearer(rich)))
			.andExpect(jsonPath("$.level").value(10));
	}

	@Test
	@DisplayName("L-1: 출석 XP도 합산된다 (출석 3일 = 15 + 업적 50)")
	void get_includesAttendance() throws Exception {
		long member = registerMember();
		for (int day = 0; day < 3; day++) {
			jdbcTemplate.update("INSERT INTO member_attendance (member_id, attended_on) VALUES (?, current_date - ?)", member, day);
		}
		mockMvc.perform(get(LEVEL).header("Authorization", bearer(member)))
			.andExpect(jsonPath("$.xp").value(65));
	}

	@Test
	@DisplayName("L-2: 열린 직업을 고르면 저장되고, 잠긴 직업은 409 JOB_LOCKED, 모르는 직업은 400")
	void selectJob_rules() throws Exception {
		long member = registerMember();
		giveXp(member, 385);
		selectJob(member, "NONE")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.job.code").value("NONE"));
		mockMvc.perform(get(LEVEL).header("Authorization", bearer(member)))
			.andExpect(jsonPath("$.job.code").value("NONE"));	// 고른 직업이 열린 가장 높은 직업보다 우선
		selectJob(member, "JUDGE").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("JOB_LOCKED"));
		selectJob(member, "WIZARD").andExpect(status().isBadRequest());
		mockMvc.perform(put(LEVEL + "/job").header("Authorization", bearer(member))
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("L-2: 무직만 고를 수 있는 신규 회원도 저장된다. 다른 회원의 직업은 바뀌지 않는다")
	void selectJob_isPerMember() throws Exception {
		long member = registerMember();
		long other = registerMember();
		giveXp(member, 385);
		giveXp(other, 385);
		selectJob(member, "NONE").andExpect(status().isOk());
		mockMvc.perform(get(LEVEL).header("Authorization", bearer(other)))
			.andExpect(jsonPath("$.job.code").value("LAW_STUDENT"));	// 안 고른 회원은 열린 가장 높은 직업 그대로
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member_profile_job WHERE member_id = ?",
			Integer.class, member)).isOne();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member_profile_job WHERE member_id = ?",
			Integer.class, other)).isZero();
	}

	@Test
	@DisplayName("L-2: 손님은 401 (쿠키 무시, 익명 ID를 발급하지 않는다)")
	void selectJob_guest() throws Exception {
		var response = mockMvc.perform(put(LEVEL + "/job").contentType(MediaType.APPLICATION_JSON).content("{\"job\":\"NONE\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andReturn().getResponse();
		assertThat(response.getHeader("Set-Cookie")).isNull();
		mockMvc.perform(put(LEVEL + "/job").cookie(new Cookie(COOKIE, UUID.randomUUID().toString()))
			.contentType(MediaType.APPLICATION_JSON).content("{\"job\":\"NONE\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("지급 상한: 글 하루 3건, 같은 글(대상)은 두 번 받지 못한다")
	void award_dailyCapAndDuplicate() {
		long member = registerMember();
		assertThat(levelService.award(member, XpSource.POST, "post-1", "")).isEqualTo(30);
		assertThat(levelService.award(member, XpSource.POST, "post-1", "")).isZero();	// 같은 글
		assertThat(levelService.award(member, XpSource.POST, "post-2", "")).isEqualTo(30);
		assertThat(levelService.award(member, XpSource.POST, "post-3", "")).isEqualTo(30);
		assertThat(levelService.award(member, XpSource.POST, "post-4", "")).isZero();	// 하루 3건 초과
		assertThat(levelService.view(member).xp()).isEqualTo(90);
	}

	@Test
	@DisplayName("지급 상한: 한 회원의 상한이 다른 회원에게 번지지 않는다")
	void award_capIsPerMember() {
		long first = registerMember();
		long second = registerMember();
		for (int i = 1; i <= 3; i++) {
			levelService.award(first, XpSource.POST, "post-" + i, "");
		}
		assertThat(levelService.award(first, XpSource.POST, "post-4", "")).isZero();
		assertThat(levelService.award(second, XpSource.POST, "post-1", "")).isEqualTo(30);	// 같은 대상 키여도 회원이 다르면 받는다
		assertThat(levelService.view(second).xp()).isEqualTo(30);
	}

	@Test
	@DisplayName("지급 상한: 좋아요 받기는 글당 10개, 다른 글은 따로 센다")
	void award_groupCap() {
		long author = registerMember();
		for (int i = 0; i < 10; i++) {
			assertThat(levelService.award(author, XpSource.LIKE_RECEIVED, "p1:liker" + i, "p1")).isEqualTo(3);
		}
		assertThat(levelService.award(author, XpSource.LIKE_RECEIVED, "p1:liker10", "p1")).isZero();	// p1은 10개 채움
		assertThat(levelService.award(author, XpSource.LIKE_RECEIVED, "p2:liker0", "p2")).isEqualTo(3);	// 다른 글
	}

	@Test
	@DisplayName("지급 상한: 오늘 상한을 어제 기록이 채우지 않는다(날짜별로 센다)")
	void award_dailyCapIsPerDay() {
		long member = registerMember();
		for (int i = 0; i < 3; i++) {
			jdbcTemplate.update("""
				INSERT INTO member_xp_event (member_id, source, ref_key, amount, awarded_on)
				VALUES (?, 'POST', ?, 30, current_date - 2)
				""", member, "old-" + i);
		}
		assertThat(levelService.award(member, XpSource.POST, "today-1", "")).isEqualTo(30);
	}

	@Test
	@DisplayName("L-3: 손님은 401, 완료하지 않은 사건은 404, 완료한 사건은 100 XP를 한 번만")
	void shareReward() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long member = registerMember();
		String auth = bearer(member);
		String body = "{\"caseId\":" + caseId + "}";

		mockMvc.perform(post(LEVEL + "/share-reward").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(post(LEVEL + "/share-reward").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isNotFound());	// 체험 기록 없음

		insertMemberCompleted(member, caseId);
		mockMvc.perform(post(LEVEL + "/share-reward").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.awardedXp").value(100));
		mockMvc.perform(post(LEVEL + "/share-reward").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.awardedXp").value(0));	// 같은 사건 · 하루 1회
		mockMvc.perform(post(LEVEL + "/share-reward").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("L-3: 다른 회원이 완료한 사건으로는 받지 못한다 (404), 완료 중인 체험도 404")
	void shareReward_otherMembersExperience() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		long owner = registerMember();
		long other = registerMember();
		insertMemberCompleted(owner, caseId);
		String body = "{\"caseId\":" + caseId + "}";

		mockMvc.perform(post(LEVEL + "/share-reward").header("Authorization", bearer(other))
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isNotFound());
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member_xp_event WHERE member_id = ?",
			Integer.class, other)).isZero();

		// 익명 주인의 완료 체험도 회원 요청에는 보이지 않는다
		long caseId2 = insertCase("FRAUD", "PUBLISHED");
		insertExperience(UUID.randomUUID(), caseId2, 1, "COMPLETED");
		mockMvc.perform(post(LEVEL + "/share-reward").header("Authorization", bearer(other))
			.contentType(MediaType.APPLICATION_JSON).content("{\"caseId\":" + caseId2 + "}"))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("P-5: 마이페이지에 level이 함께 내려온다 (손님은 401)")
	void myPage_hasLevel() throws Exception {
		mockMvc.perform(get("/api/v1/my-page")).andExpect(status().isUnauthorized());
		long member = registerMember();
		mockMvc.perform(get("/api/v1/my-page").header("Authorization", bearer(member)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.level.level").value(1))
			.andExpect(jsonPath("$.level.job.name").value("무직"));
		giveXp(member, 385);
		mockMvc.perform(get("/api/v1/my-page").header("Authorization", bearer(member)))
			.andExpect(jsonPath("$.level.level").value(10));
	}
}
