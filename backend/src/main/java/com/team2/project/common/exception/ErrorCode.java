package com.team2.project.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 에러 코드 (API 명세 1-5 표와 1:1)
 * 새 에러는 여기에 한 줄 추가하고 BusinessException(ErrorCode)로 던진다.
 * 하위 예외 클래스는 추가 정보(필드)가 필요하거나 여러 곳에서 같은 의미로 던질 때만 만든다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	CASE_NOT_FOUND(HttpStatus.NOT_FOUND, "사건을 찾을 수 없습니다."),
	EXPERIENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "이 사건의 체험 기록이 없습니다."),
	INVALID_STATE(HttpStatus.CONFLICT, "지금 단계에서는 할 수 없는 요청입니다."),
	STEP_OUT_OF_ORDER(HttpStatus.CONFLICT, "섹션 확인 순서를 건너뛸 수 없습니다."),
	INVALID_RANGE_OPTION(HttpStatus.UNPROCESSABLE_CONTENT, "선택할 수 없는 형량 구간입니다."),
	INVALID_FACTOR(HttpStatus.UNPROCESSABLE_CONTENT, "판단 요소가 올바르지 않습니다."),
	TOO_MANY_FACTORS(HttpStatus.UNPROCESSABLE_CONTENT, "작용 요소는 2개까지 고를 수 있습니다."),
	INVALID_PENALTY_TYPE(HttpStatus.UNPROCESSABLE_CONTENT, "선택할 수 없는 형벌입니다."),
	OUT_OF_ALLOWED_RANGE(HttpStatus.UNPROCESSABLE_CONTENT, "선고할 수 있는 범위를 벗어났습니다."),
	INVALID_SUSPENSION(HttpStatus.UNPROCESSABLE_CONTENT, "집행유예를 적용할 수 없습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final HttpStatus status;

	private final String message;	// 기본 메시지 (화면에 그대로 띄우지 않는다, API 명세 1-4)
}
