package com.team2.project.common.exception;

/** API 명세서 1-4 에러 응답. currentStatus · details는 해당하지 않으면 null. */
public record ApiErrorResponse(String code, String message, String currentStatus, Object details) {

	public static ApiErrorResponse of(ErrorCode errorCode) {
		return of(errorCode, null);
	}

	public static ApiErrorResponse of(ErrorCode errorCode, String currentStatus) {
		return new ApiErrorResponse(errorCode.name(), errorCode.getMessage(), currentStatus, null);
	}
}
