package com.team2.project.attendance.service;

import com.team2.project.attendance.config.AttendanceProperties;
import com.team2.project.attendance.repository.AttendanceRepository;
import com.team2.project.experience.service.AnonymousUserService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출석 기록 · 출석 일수 (BE-97, docs/decisions/BE-97.md)
 * 익명 ID 쿠키가 있을 때만 기록한다(새로 발급하지 않는다). 하루 한 번만 센다.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

	private final AttendanceRepository attendanceRepository;
	private final AnonymousUserService anonymousUserService;
	private final AttendanceProperties properties;
	private final Clock clock;

	/**
	 * 오늘 출석을 남긴다. 쿠키가 없거나 DB에 없는 익명 ID면 아무것도 하지 않는다(외래 키 위반 방지).
	 * 같은 날 여러 번 불러도 결과가 같다(insertIfAbsent)
	 */
	@Transactional
	public void record(Optional<UUID> anonymousId) {
		anonymousUserService.findAndTouch(anonymousId)
			.ifPresent(user -> attendanceRepository.insertIfAbsent(user.getId(), today()));
	}

	/** 누적 출석 일수. 기록이 없으면 0 */
	@Transactional(readOnly = true)
	public int countDays(UUID anonymousUserId) {
		return Math.toIntExact(attendanceRepository.countByIdAnonymousUserId(anonymousUserId));
	}

	/** 출석으로 칠 날짜: 설정 시간대의 지금 시각에서 하루 시작 시각만큼 뺀 날짜 */
	LocalDate today() {
		return clock.instant().atZone(properties.zone()).minusHours(properties.dayStartHour()).toLocalDate();
	}
}
