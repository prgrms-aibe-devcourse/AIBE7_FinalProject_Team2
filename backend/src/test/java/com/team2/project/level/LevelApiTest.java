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
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 레벨 · 직업 조회 · 선택 · SNS 공유 보상 · 경험치 지급 상한 (L-1 ~ L-3, BE-106) */
class LevelApiTest extends ApiIntegrationTest {

	private static final String LEVEL = "/api/v1/level";

	@Autowired
	private LevelService levelService;

	private final List<UUID> userIds = new ArrayList<>();

	@AfterEach
	void deleteUsers() {
		userIds.forEach(id -> {
			jdbcTemplate.update("DELETE FROM xp_event WHERE anonymous_user_id = ?", id);
			jdbcTemplate.update("DELETE FROM profile_job WHERE anonymous_user_id = ?", id);
			jdbcTemplate.update("DELETE FROM attendance WHERE anonymous_user_id = ?", id);
			jdbcTemplate.update("DELETE FROM anonymous_user WHERE id = ?", id);
		});
		userIds.clear();
	}

	private UUID newUser() {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO anonymous_user (id) VALUES (?)", id);
		userIds.add(id);
		return id;
	}

	/** 지급 상한과 상관없이 XP만 채워 레벨을 올린다 */
	private void giveXp(UUID userId, int amount) {
		jdbcTemplate.update("""
			INSERT INTO xp_event (anonymous_user_id, source, ref_key, amount, awarded_on)
			VALUES (?, 'POST', ?, ?, current_date - 30)
			""", userId, "seed-" + UUID.randomUUID(), amount);
	}

	private ResultActions selectJob(UUID userId, String job) throws Exception {
		return mockMvc.perform(put(LEVEL + "/job").cookie(new Cookie(COOKIE, userId.toString()))
			.contentType(MediaType.APPLICATION_JSON).content("{\"job\":\"" + job + "\"}"));
	}

	@Test
	@DisplayName("L-1: 쿠키가 없거나 기록이 없으면 Lv1 · 0 XP · 무직, 직업 11개")
	void get_initial() throws Exception {
		mockMvc.perform(get(LEVEL))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.level").value(1))
			.andExpect(jsonPath("$.xp").value(0))
			.andExpect(jsonPath("$.nextLevelXp").value(41))
			.andExpect(jsonPath("$.job.code").value("NONE"))
			.andExpect(jsonPath("$.jobs.length()").value(11));
		mockMvc.perform(get(LEVEL).cookie(new Cookie(COOKIE, newUser().toString())))
			.andExpect(jsonPath("$.level").value(1))
			.andExpect(jsonPath("$.job.name").value("무직"));
	}

	@Test
	@DisplayName("L-1: 원장 XP가 쌓이면 레벨이 오르고 직업이 열린다 (385 → Lv10 로스쿨생)")
	void get_afterXp() throws Exception {
		UUID user = newUser();
		giveXp(user, 385);
		mockMvc.perform(get(LEVEL).cookie(new Cookie(COOKIE, user.toString())))
			.andExpect(jsonPath("$.level").value(10))
			.andExpect(jsonPath("$.xp").value(385))
			.andExpect(jsonPath("$.currentLevelXp").value(385))
			.andExpect(jsonPath("$.job.code").value("LAW_STUDENT"))
			.andExpect(jsonPath("$.jobs[1].unlocked").value(true))
			.andExpect(jsonPath("$.jobs[2].unlocked").value(false));
	}

	@Test
	@DisplayName("L-1: 출석 XP도 합산된다 (출석 3일 = 15 + 업적 50)")
	void get_includesAttendance() throws Exception {
		UUID user = newUser();
		for (int day = 0; day < 3; day++) {
			jdbcTemplate.update("INSERT INTO attendance (anonymous_user_id, attended_on) VALUES (?, current_date - ?)", user, day);
		}
		mockMvc.perform(get(LEVEL).cookie(new Cookie(COOKIE, user.toString())))
			.andExpect(jsonPath("$.xp").value(65));
	}

	@Test
	@DisplayName("L-2: 열린 직업을 고르면 저장되고, 잠긴 직업은 409 JOB_LOCKED, 모르는 직업은 400")
	void selectJob_rules() throws Exception {
		UUID user = newUser();
		giveXp(user, 385);
		selectJob(user, "NONE")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.job.code").value("NONE"));
		mockMvc.perform(get(LEVEL).cookie(new Cookie(COOKIE, user.toString())))
			.andExpect(jsonPath("$.job.code").value("NONE"));	// 고른 직업이 열린 가장 높은 직업보다 우선
		selectJob(user, "JUDGE").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("JOB_LOCKED"));
		selectJob(user, "WIZARD").andExpect(status().isBadRequest());
		mockMvc.perform(put(LEVEL + "/job").cookie(new Cookie(COOKIE, user.toString()))
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("L-2: 쿠키가 없으면 익명 ID를 발급하고 무직만 고를 수 있다")
	void selectJob_withoutCookie() throws Exception {
		var response = mockMvc.perform(put(LEVEL + "/job").contentType(MediaType.APPLICATION_JSON).content("{\"job\":\"NONE\"}"))
			.andExpect(status().isOk()).andReturn().getResponse();
		String setCookie = response.getHeader("Set-Cookie");
		assertThat(setCookie).contains(COOKIE + "=");
		String id = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
		userIds.add(UUID.fromString(id));
	}

	@Test
	@DisplayName("지급 상한: 글 하루 3건, 같은 글(대상)은 두 번 받지 못한다")
	void award_dailyCapAndDuplicate() {
		UUID user = newUser();
		assertThat(levelService.award(user, XpSource.POST, "post-1", "")).isEqualTo(30);
		assertThat(levelService.award(user, XpSource.POST, "post-1", "")).isZero();	// 같은 글
		assertThat(levelService.award(user, XpSource.POST, "post-2", "")).isEqualTo(30);
		assertThat(levelService.award(user, XpSource.POST, "post-3", "")).isEqualTo(30);
		assertThat(levelService.award(user, XpSource.POST, "post-4", "")).isZero();	// 하루 3건 초과
		assertThat(levelService.view(user).xp()).isEqualTo(90);
	}

	@Test
	@DisplayName("지급 상한: 좋아요 받기는 글당 10개, 다른 글은 따로 센다")
	void award_groupCap() {
		UUID author = newUser();
		for (int i = 0; i < 10; i++) {
			assertThat(levelService.award(author, XpSource.LIKE_RECEIVED, "p1:liker" + i, "p1")).isEqualTo(3);
		}
		assertThat(levelService.award(author, XpSource.LIKE_RECEIVED, "p1:liker10", "p1")).isZero();	// p1은 10개 채움
		assertThat(levelService.award(author, XpSource.LIKE_RECEIVED, "p2:liker0", "p2")).isEqualTo(3);	// 다른 글
	}

	@Test
	@DisplayName("지급 상한: 오늘 상한을 어제 기록이 채우지 않는다(날짜별로 센다)")
	void award_dailyCapIsPerDay() {
		UUID user = newUser();
		for (int i = 0; i < 3; i++) {
			jdbcTemplate.update("""
				INSERT INTO xp_event (anonymous_user_id, source, ref_key, amount, awarded_on)
				VALUES (?, 'POST', ?, 30, current_date - 2)
				""", user, "old-" + i);
		}
		assertThat(levelService.award(user, XpSource.POST, "today-1", "")).isEqualTo(30);
	}

	@Test
	@DisplayName("L-3: 완료하지 않은 사건 · 쿠키 없음은 404, 완료한 사건은 100 XP를 한 번만")
	void shareReward() throws Exception {
		long caseId = insertCase("MURDER", "PUBLISHED");
		UUID user = newUser();
		Cookie cookie = new Cookie(COOKIE, user.toString());
		String body = "{\"caseId\":" + caseId + "}";

		mockMvc.perform(post(LEVEL + "/share-reward").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isNotFound());
		mockMvc.perform(post(LEVEL + "/share-reward").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isNotFound());	// 체험 기록 없음

		jdbcTemplate.update("""
			INSERT INTO experience (anonymous_user_id, case_id, status, completed_at) VALUES (?, ?, 'COMPLETED', now())
			""", user, caseId);
		try {
			mockMvc.perform(post(LEVEL + "/share-reward").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.awardedXp").value(100));
			mockMvc.perform(post(LEVEL + "/share-reward").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.awardedXp").value(0));	// 같은 사건 · 하루 1회
			mockMvc.perform(post(LEVEL + "/share-reward").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		} finally {
			jdbcTemplate.update("DELETE FROM xp_event WHERE anonymous_user_id = ?", user);
			jdbcTemplate.update("DELETE FROM experience WHERE anonymous_user_id = ?", user);
		}
	}

	@Test
	@DisplayName("P-5: 마이페이지에 level이 함께 내려온다")
	void myPage_hasLevel() throws Exception {
		mockMvc.perform(get("/api/v1/my-page"))
			.andExpect(jsonPath("$.level.level").value(1))
			.andExpect(jsonPath("$.level.job.name").value("무직"));
		UUID user = newUser();
		giveXp(user, 385);
		mockMvc.perform(get("/api/v1/my-page").cookie(new Cookie(COOKIE, user.toString())))
			.andExpect(jsonPath("$.level.level").value(10));
	}
}
