package com.team2.project.judgment.domain;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.exception.FieldErrorDetail;
import java.util.List;
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
		SUSPENSION_NOT_ALLOWED(ErrorCode.INVALID_SUSPENSION),	// 사형 · 무기를 고른 뒤 집행유예 (Judgment.userFinal)
		MISSING_DIRECTION(ErrorCode.INVALID_FACTOR),			// 최종 판결 판단 요소에 방향 없음 (JudgmentFactor.forFinal)
		PENALTY_NOT_OFFERED(ErrorCode.INVALID_PENALTY_TYPE),	// 이 사건의 penalty_rule에 없는 형벌을 고름 (BE-9)
		OUT_OF_ALLOWED_RANGE(ErrorCode.OUT_OF_ALLOWED_RANGE),	// 선고 가능 범위(allowedMin ~ allowedMax)를 벗어난 형량 (BE-9)
		SUSPENSION_CONDITION(ErrorCode.INVALID_SUSPENSION),	// 집행유예 법정 조건(형법 제62조)을 벗어남 (BE-9)
		INVALID_FACTOR(ErrorCode.INVALID_FACTOR);				// 이 사건에 없는 요소 · 중복 · 방향 오류 (BE-9)

		private final ErrorCode errorCode;

		Reason(ErrorCode errorCode) {
			this.errorCode = errorCode;
		}
	}

	private final Reason reason;

	/** 필드별 사유가 있는 경우(예: OUT_OF_ALLOWED_RANGE)의 그 필드 이름. 없으면 null (BE-9) */
	private final String field;

	public InvalidJudgmentException(Reason reason, String message) {
		this(reason, message, null);
	}

	/**
	 * field가 있으면 공통 에러 응답의 details에도 { "field": field, "reason": 에러코드 } 한 건으로 담긴다
	 * (API 명세 1-4 · 1-5 OUT_OF_ALLOWED_RANGE 예시와 동일한 형식)
	 */
	public InvalidJudgmentException(Reason reason, String message, String field) {
		super(reason.getErrorCode(), message, null,
			field == null ? null : List.of(new FieldErrorDetail(field, reason.getErrorCode().name())));
		this.reason = reason;
		this.field = field;
	}
}
