package com.team2.project.common.exception;

import java.util.List;
import lombok.Getter;

/**
 * 모든 비즈니스 예외의 부모. ApiExceptionAdvice가 ErrorCode 기준으로 공통 에러 응답으로 바꾼다.
 */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	private final String currentStatus;	// 체험 관련 에러일 때 현재 상태 이름 (화면 이동용, API 명세 1-4)

	private final List<FieldErrorDetail> details;	// 입력 검증 에러일 때 필드별 사유

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, errorCode.getMessage());
	}

	public BusinessException(ErrorCode errorCode, String message) {
		this(errorCode, message, null, null);
	}

	public BusinessException(ErrorCode errorCode, String message, String currentStatus,
		List<FieldErrorDetail> details) {
		super(message);
		this.errorCode = errorCode;
		this.currentStatus = currentStatus;
		this.details = details;
	}

	/** 필드 하나에 대한 입력 검증 에러 (예: 선고 가능 범위 밖 → field prisonMonths) */
	public static BusinessException ofField(ErrorCode errorCode, String field) {
		return new BusinessException(errorCode, errorCode.getMessage(), null,
			List.of(new FieldErrorDetail(field, errorCode.name())));
	}
}
