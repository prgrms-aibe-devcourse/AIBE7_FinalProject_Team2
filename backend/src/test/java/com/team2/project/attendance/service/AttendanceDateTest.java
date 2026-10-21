package com.team2.project.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.attendance.config.AttendanceProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 출석 날짜 계산 (BE-97): 설정 시간대 · 하루 시작 시각 기준 */
class AttendanceDateTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private static LocalDate todayAt(String kstDateTime, int dayStartHour) {
		Instant now = java.time.LocalDateTime.parse(kstDateTime).atZone(KST).toInstant();
		AttendanceService service = new AttendanceService(null, null, new AttendanceProperties(KST, dayStartHour),
			Clock.fixed(now, ZoneOffset.UTC));
		return service.today();
	}

	@Test
	@DisplayName("기본(자정 기준): 한국 시각 날짜를 그대로 쓴다 — UTC로는 전날이어도")
	void midnightUsesKstDate() {
		// 10/22 08:59 KST = 10/21 23:59 UTC
		assertThat(todayAt("2026-10-22T08:59", 0)).isEqualTo(LocalDate.of(2026, 10, 22));
		assertThat(todayAt("2026-10-22T00:00", 0)).isEqualTo(LocalDate.of(2026, 10, 22));
		assertThat(todayAt("2026-10-21T23:59", 0)).isEqualTo(LocalDate.of(2026, 10, 21));
	}

	@Test
	@DisplayName("새벽 4시 기준: 4시 전 접속은 전날 출석")
	void dayStartHourShiftsBoundary() {
		assertThat(todayAt("2026-10-22T03:59", 4)).isEqualTo(LocalDate.of(2026, 10, 21));
		assertThat(todayAt("2026-10-22T04:00", 4)).isEqualTo(LocalDate.of(2026, 10, 22));
	}

	@Test
	@DisplayName("하루 시작 시각이 0 ~ 23을 벗어나면 서버가 뜨지 않는다")
	void invalidDayStartHour() {
		assertThatThrownBy(() -> new AttendanceProperties(KST, 24)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new AttendanceProperties(KST, -1)).isInstanceOf(IllegalStateException.class);
	}
}
