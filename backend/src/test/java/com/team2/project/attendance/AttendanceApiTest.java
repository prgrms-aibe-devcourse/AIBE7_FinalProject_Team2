package com.team2.project.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 출석 API (BE-97) · 마이페이지 profile.attendanceDays.
 * 시각을 고정해 두 요청 사이에 자정이 지나도 결과가 같게 한다 (#227 리뷰). 고정 시각은 KST 2026-03-02 03:30
 */
class AttendanceApiTest extends ApiIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-03-01T18:30:00Z");
	private static final LocalDate TODAY_KST = LocalDate.of(2026, 3, 2);

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClock {

		@Bean
		@Primary	// TimeConfig의 시스템 시계 대신 이 시계를 주입한다
		Clock fixedClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}

	private static final String ATTENDANCE = "/api/v1/attendance";
	private static final String MY_PAGE = "/api/v1/my-page";

	private final List<UUID> createdUserIds = new ArrayList<>();

	@AfterEach
	void deleteAttendance() {
		createdUserIds.forEach(id -> {
			jdbcTemplate.update("DELETE FROM attendance WHERE anonymous_user_id = ?", id);
			jdbcTemplate.update("DELETE FROM anonymous_user WHERE id = ?", id);
		});
		createdUserIds.clear();
	}

	private UUID newUser() {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO anonymous_user (id) VALUES (?)", id);
		createdUserIds.add(id);
		return id;
	}

	private int countRows(UUID id) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM attendance WHERE anonymous_user_id = ?", Integer.class, id);
	}

	@Test
	@DisplayName("같은 날 여러 번 출석해도 하루로 센다 (204)")
	void sameDayCountsOnce() throws Exception {
		UUID id = newUser();
		Cookie cookie = new Cookie(COOKIE, id.toString());

		mockMvc.perform(post(ATTENDANCE).cookie(cookie)).andExpect(status().isNoContent());
		mockMvc.perform(post(ATTENDANCE).cookie(cookie)).andExpect(status().isNoContent());

		assertThat(countRows(id)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT attended_on FROM attendance WHERE anonymous_user_id = ?",
			LocalDate.class, id)).isEqualTo(TODAY_KST);	// UTC로는 3/1이지만 KST 날짜로 저장된다
	}

	@Test
	@DisplayName("쿠키가 없거나 DB에 없는 익명 ID면 기록하지 않고 발급도 하지 않는다 (204)")
	void noCookieOrUnknownIdIsIgnored() throws Exception {
		mockMvc.perform(post(ATTENDANCE))
			.andExpect(status().isNoContent())
			.andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie")).isNull());

		UUID unknown = UUID.randomUUID();
		mockMvc.perform(post(ATTENDANCE).cookie(new Cookie(COOKIE, unknown.toString())))
			.andExpect(status().isNoContent());

		assertThat(countRows(unknown)).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM anonymous_user WHERE id = ?", Integer.class, unknown))
			.isZero();
	}

	@Test
	@DisplayName("쿠키 없이 체험을 시작하면 발급과 함께 그날 출석이 남고, 이어 출석 API를 불러도 하루다 (BE-98)")
	void issuingCookieRecordsAttendance() throws Exception {
		Cookie cookie = startAndGetCookie(insertCase("MURDER", "PUBLISHED"));	// 부모가 사건 · 체험 · 사용자 · 출석을 지운다
		UUID id = UUID.fromString(cookie.getValue());

		assertThat(countRows(id)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT attended_on FROM attendance WHERE anonymous_user_id = ?",
			LocalDate.class, id)).isEqualTo(TODAY_KST);

		mockMvc.perform(post(ATTENDANCE).cookie(cookie)).andExpect(status().isNoContent());
		assertThat(countRows(id)).isEqualTo(1);
	}

	@Test
	@DisplayName("마이페이지 profile.attendanceDays는 누적 출석 일수다")
	void myPageShowsAttendanceDays() throws Exception {
		UUID id = newUser();
		Cookie cookie = new Cookie(COOKIE, id.toString());
		LocalDate base = LocalDate.of(2026, 1, 1);	// 고정한 오늘(3/2)과 겹치지 않는 지난 날짜
		jdbcTemplate.update("INSERT INTO attendance (anonymous_user_id, attended_on) VALUES (?, ?), (?, ?)",
			id, base, id, base.plusDays(1));

		mockMvc.perform(post(ATTENDANCE).cookie(cookie)).andExpect(status().isNoContent());

		mockMvc.perform(get(MY_PAGE).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profile.attendanceDays").value(3));
	}

	@Test
	@DisplayName("쿠키가 없으면 빈 마이페이지의 출석 일수는 0")
	void emptyMyPageHasZeroDays() throws Exception {
		mockMvc.perform(get(MY_PAGE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profile.attendanceDays").value(0));
	}
}
