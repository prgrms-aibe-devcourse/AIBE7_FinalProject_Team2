package com.team2.project.attendance.controller;

import com.team2.project.attendance.service.AttendanceService;
import com.team2.project.common.web.AnonymousIdCookie;
import jakarta.servlet.http.HttpServletRequest;
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
	private final AnonymousIdCookie anonymousIdCookie;

	/** 오늘 출석 기록 — 쿠키가 없어도 · 이미 출석했어도 204 (익명 ID를 새로 발급하지 않는다) */
	@PostMapping("/attendance")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void record(HttpServletRequest request) {
		attendanceService.record(anonymousIdCookie.read(request));
	}
}
