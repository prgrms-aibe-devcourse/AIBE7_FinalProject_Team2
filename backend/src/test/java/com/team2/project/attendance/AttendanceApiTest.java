package com.team2.project.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.support.ApiIntegrationTest;
import com.team2.project.support.FakeMemberTokens;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * 출석 API (BE-97, BE-140 회원 키)  · 마이페이지 profile.attendanceDays.
 * 시각을 고정해 두 요청 사이에 자정이 지나도 결과가 같게 한다 (#227 리뷰). 고정 시각은 KST 2026-03-02 03:30
 */
@Import(FakeMemberTokens.class)
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

	private int countRows(long memberId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM member_attendance WHERE member_id = ?", Integer.class, memberId);
	}

	@Test
	@DisplayName("같은 날 여러 번 출석해도 하루로 센다 (204)")
	void sameDayCountsOnce() throws Exception {
		long id = registerMember();

		mockMvc.perform(post(ATTENDANCE).header("Authorization", bearer(id))).andExpect(status().isNoContent());
		mockMvc.perform(post(ATTENDANCE).header("Authorization", bearer(id))).andExpect(status().isNoContent());

		assertThat(countRows(id)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT attended_on FROM member_attendance WHERE member_id = ?",
			LocalDate.class, id)).isEqualTo(TODAY_KST);	// UTC로는 3/1이지만 KST 날짜로 저장된다
	}

	@Test
	@DisplayName("비회원이 출석해도 204이지만 아무것도 기록하지 않고 쿠키도 발급하지 않는다")
	void guestIsNoOp() throws Exception {
		int before = jdbcTemplate.queryForObject("SELECT count(*) FROM member_attendance", Integer.class);

		mockMvc.perform(post(ATTENDANCE))
			.andExpect(status().isNoContent())
			.andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie")).isNull());
		mockMvc.perform(post(ATTENDANCE).cookie(new jakarta.servlet.http.Cookie(COOKIE, java.util.UUID.randomUUID().toString())))
			.andExpect(status().isNoContent());

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member_attendance", Integer.class)).isEqualTo(before);
	}

	@Test
	@DisplayName("마이페이지 profile.attendanceDays는 누적 출석 일수다")
	void myPageShowsAttendanceDays() throws Exception {
		long id = registerMember();
		LocalDate base = LocalDate.of(2026, 1, 1);	// 고정한 오늘(3/2)과 겹치지 않는 지난 날짜
		jdbcTemplate.update("INSERT INTO member_attendance (member_id, attended_on) VALUES (?, ?), (?, ?)",
			id, base, id, base.plusDays(1));

		mockMvc.perform(post(ATTENDANCE).header("Authorization", bearer(id))).andExpect(status().isNoContent());

		mockMvc.perform(get(MY_PAGE).header("Authorization", bearer(id)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profile.attendanceDays").value(3));
	}

	@Test
	@DisplayName("다른 회원의 출석은 내 마이페이지에 세지 않는다")
	void otherMemberAttendanceNotVisible() throws Exception {
		long mine = registerMember();
		long other = registerMember();
		jdbcTemplate.update("INSERT INTO member_attendance (member_id, attended_on) VALUES (?, ?)", other, LocalDate.of(2026, 1, 1));

		mockMvc.perform(get(MY_PAGE).header("Authorization", bearer(mine)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profile.attendanceDays").value(0));
		assertThat(countRows(mine)).isZero();
	}

	@Test
	@DisplayName("비회원 마이페이지는 401 UNAUTHORIZED")
	void guestMyPageUnauthorized() throws Exception {
		mockMvc.perform(get(MY_PAGE))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
}
