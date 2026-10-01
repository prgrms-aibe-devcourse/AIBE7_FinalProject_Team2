package com.team2.project.common.exception;

/**
 * 입력 검증 에러의 필드별 사유. 예: { "field": "prisonMonths", "reason": "OUT_OF_ALLOWED_RANGE" }
 */
public record FieldErrorDetail(String field, String reason) {
}
