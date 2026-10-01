package com.team2.project.judgment.domain;

import lombok.Getter;

/**
 * 판결 값 조합이 규칙에 맞지 않음 (형벌 종류 · 감경 조합 · 형량 값 · 판단 요소 방향)
 * 사유(Reason)별로 API 에러 코드가 정해져 있어, 공통 예외 처리(BE-4)는 사유만 보고 변환하면 된다.
 */
@Getter
public class InvalidJudgmentException extends RuntimeException {

	/** 판결 오류 사유와 대응하는 API 에러 코드 (API 명세 1-5) */
	public enum Reason {
		MISSING_PENALTY_TYPE("VALIDATION_ERROR"),		// 최종 판결에 형벌 종류 없음
		INVALID_REDUCTION("INVALID_PENALTY_TYPE"),		// 허용되지 않은 감경 조합 (사형 → 무기 · 징역, 무기 → 징역만)
		INVALID_TERM_VALUES("VALIDATION_ERROR"),		// 형벌 종류에 맞지 않는 개월 · 금액 조합, 0 이하 값
		SUSPENSION_NOT_ALLOWED("INVALID_SUSPENSION"),	// 사형 · 무기를 고른 뒤 집행유예
		MISSING_DIRECTION("INVALID_FACTOR"),			// 최종 판결 판단 요소에 방향 없음
		PENALTY_NOT_OFFERED("INVALID_PENALTY_TYPE"),
		OUT_OF_ALLOWED_RANGE("OUT_OF_ALLOWED_RANGE"),
		SUSPENSION_CONDITION("INVALID_SUSPENSION"),
		INVALID_FACTOR("INVALID_FACTOR");

		private final String apiErrorCode;

		Reason(String apiErrorCode) {
			this.apiErrorCode = apiErrorCode;
		}

		public String getApiErrorCode() {
			return apiErrorCode;
		}
	}

	private final Reason reason;
	private final String field;

	public InvalidJudgmentException(Reason reason, String message) {
		super(message);
		this.reason = reason;
		this.field = null;
	}

	public InvalidJudgmentException(Reason reason, String message, String field) {
		super(message);
		this.reason = reason;
		this.field = field;
	}
}
