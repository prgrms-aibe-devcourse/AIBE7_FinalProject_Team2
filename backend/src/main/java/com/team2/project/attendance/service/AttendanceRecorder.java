package com.team2.project.attendance.service;

import com.team2.project.attendance.config.AttendanceProperties;
import com.team2.project.attendance.repository.AttendanceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출석 날짜 계산 · 한 줄 기록 (BE-97, BE-98에서 분리).
 * 출석 API(AttendanceService)와 익명 ID 발급(AnonymousUserService.getOrIssue)이 함께 쓴다.
 * 익명 사용자를 조회하지 않아(AnonymousUserService를 모른다) 두 서비스가 서로를 부르는 순환이 생기지 않는다.
 * 넘겨받은 익명 ID는 DB에 이미 있어야 한다(외래 키)
 */
@Component
@RequiredArgsConstructor
public class AttendanceRecorder {

	private final AttendanceRepository attendanceRepository;
	private final AttendanceProperties properties;
	private final Clock clock;

	/** 그날 출석을 남긴다. 같은 날 다시 불러도 · 동시에 불려도 한 줄이다(insertIfAbsent) */
	@Transactional
	public void record(UUID anonymousUserId) {
		attendanceRepository.insertIfAbsent(anonymousUserId, today());
	}

	/**
	 * 출석으로 칠 날짜: 설정 시간대의 현지 시각(벽시계)에서 하루 시작 시각만큼 뺀 날짜.
	 * ZonedDateTime에서 바로 빼면 경과 시간 기준이라 서머타임 전환일에 한 시간 어긋나므로, 현지 시각(LocalDateTime)으로 바꾼 뒤 뺀다 (#227 리뷰)
	 */
	LocalDate today() {
		return clock.instant().atZone(properties.zone()).toLocalDateTime().minusHours(properties.dayStartHour()).toLocalDate();
	}
}
