package com.team2.project.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	/** 체험 상태 관련 에러일 때만 값이 있다. 화면은 이 값으로 이동할 화면을 정한다 (API 명세서 1-6). */
	private final String currentStatus;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, null);
	}

	public BusinessException(ErrorCode errorCode, String currentStatus) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.currentStatus = currentStatus;
	}
}
