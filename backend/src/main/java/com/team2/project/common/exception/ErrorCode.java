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
	INVALID_STATE(HttpStatus.CONFLICT, "지금 단계에서는 할 수 없는 요청입니다."),
	INVALID_RANGE_OPTION(HttpStatus.UNPROCESSABLE_CONTENT, "이 사건의 사전 판단 형량 구간이 아닙니다."),
	INVALID_FACTOR(HttpStatus.UNPROCESSABLE_CONTENT, "사전 판단에 쓸 수 없는 판단 요소입니다."),
	TOO_MANY_FACTORS(HttpStatus.UNPROCESSABLE_CONTENT, "사전 판단에 고를 수 있는 판단 요소는 2개까지입니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String message;
}
