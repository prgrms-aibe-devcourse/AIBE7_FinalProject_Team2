package com.team2.project.common.exception;

import java.util.List;

/**
 * 공통 에러 응답 (API 명세 1-4). 성공 응답은 감싸지 않고, 실패 응답만 이 형식을 쓴다.
 * currentStatus · details는 해당할 때만 값이 있고, 없으면 null로 응답한다.
 */
public record ErrorResponse(
	String code,
	String message,
	String currentStatus,
	List<FieldErrorDetail> details
) {

	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(errorCode.name(), errorCode.getMessage(), null, null);
	}

	public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorDetail> details) {
		return new ErrorResponse(errorCode.name(), errorCode.getMessage(), null, details);
	}

	public static ErrorResponse from(BusinessException e) {
		return new ErrorResponse(e.getErrorCode().name(), e.getMessage(), e.getCurrentStatus(), e.getDetails());
	}
}
