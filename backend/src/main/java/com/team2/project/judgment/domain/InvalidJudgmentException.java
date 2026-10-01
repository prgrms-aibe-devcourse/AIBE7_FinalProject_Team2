package com.team2.project.judgment.domain;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import lombok.Getter;

/**
 * 판결 값 조합이 규칙에 맞지 않음 (형벌 종류 · 감경 조합 · 형량 값 · 판단 요소 방향)
 * 사유(Reason)마다 API 에러 코드가 정해져 있다 (API 명세 1-5)
 */
@Getter
public class InvalidJudgmentException extends BusinessException {

	/** 판결 오류 사유와 대응하는 에러 코드 */
	@Getter
	public enum Reason {
		MISSING_PENALTY_TYPE(ErrorCode.VALIDATION_ERROR),		// 최종 판결에 형벌 종류 없음
		INVALID_REDUCTION(ErrorCode.INVALID_PENALTY_TYPE),		// 허용되지 않은 감경 조합 (사형 → 무기 · 징역, 무기 → 징역만)
		INVALID_TERM_VALUES(ErrorCode.VALIDATION_ERROR),		// 형벌 종류에 맞지 않는 개월 · 금액 조합, 0 이하 값
		SUSPENSION_NOT_ALLOWED(ErrorCode.INVALID_SUSPENSION),	// 사형 · 무기를 고른 뒤 집행유예
		MISSING_DIRECTION(ErrorCode.INVALID_FACTOR);			// 최종 판결 판단 요소에 방향 없음

		private final ErrorCode errorCode;

		Reason(ErrorCode errorCode) {
			this.errorCode = errorCode;
		}
	}

	private final Reason reason;

	public InvalidJudgmentException(Reason reason, String message) {
		super(reason.getErrorCode(), message);
		this.reason = reason;
	}
}
