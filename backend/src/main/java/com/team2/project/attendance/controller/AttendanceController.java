package com.team2.project.attendance.controller;

import com.team2.project.attendance.service.AttendanceService;
import com.team2.project.common.web.Requester;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 출석 API (BE-97). 프론트가 사이트에 들어올 때 한 번 부른다 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AttendanceController {

	private final AttendanceService attendanceService;

	/** 오늘 출석 기록 — 로그인하지 않았어도 · 이미 출석했어도 204 (손님은 기록하지 않는다, BE-140) */
	@PostMapping("/attendance")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void record(Requester requester) {
		attendanceService.record(requester);
	}
}
