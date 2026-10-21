package com.team2.project.attendance.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 출석 날짜 기준 (BE-97, docs/decisions/BE-97.md)
 *
 * @param zone         하루를 나누는 시간대
 * @param dayStartHour 하루가 시작되는 시각(0 ~ 23). 4면 새벽 4시 전 접속은 전날 출석으로 센다.
 *                     바꾸면 그 뒤 기록부터 적용된다(이미 저장된 날짜는 그대로)
 */
@ConfigurationProperties("attendance")
public record AttendanceProperties(
	@DefaultValue("Asia/Seoul") ZoneId zone,
	@DefaultValue("0") int dayStartHour
) {

	public AttendanceProperties {
		if (dayStartHour < 0 || dayStartHour > 23) {
			throw new IllegalStateException("attendance.day-start-hour는 0 ~ 23이어야 한다: " + dayStartHour);
		}
	}
}
