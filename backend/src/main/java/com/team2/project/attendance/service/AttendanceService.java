package com.team2.project.attendance.service;

import com.team2.project.attendance.repository.AttendanceRepository;
import com.team2.project.common.web.Requester;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출석 기록 · 출석 일수 (BE-97, docs/decisions/BE-97.md). 출석은 회원만 남긴다(BE-140, member_attendance).
 * 출석 API는 로그인했을 때만 기록하고 하루 한 번만 센다. 손님은 아무것도 하지 않고 지나간다(앱 시작 때 모두가 부르므로 401로 막지 않는다)
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

	private final AttendanceRepository attendanceRepository;
	private final AttendanceRecorder recorder;

	/** 로그인한 회원의 오늘 출석을 남긴다. 손님이면 아무것도 하지 않는다. 같은 날 여러 번 불러도 결과가 같다(insertIfAbsent) */
	@Transactional
	public void record(Requester requester) {
		requester.memberId().ifPresent(recorder::record);
	}

	/** 누적 출석 일수. 기록이 없으면 0 */
	@Transactional(readOnly = true)
	public int countDays(long memberId) {
		return Math.toIntExact(attendanceRepository.countByIdMemberId(memberId));
	}
}
