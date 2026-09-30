package com.team2.project.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** API 명세서 1-5 에러 코드. 화면 분기는 code 값으로 하고, message는 화면에 그대로 띄우지 않는다. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	CASE_NOT_FOUND(HttpStatus.NOT_FOUND, "사건을 찾을 수 없습니다."),
	EXPERIENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "이 사건의 체험을 찾을 수 없습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String message;
}
