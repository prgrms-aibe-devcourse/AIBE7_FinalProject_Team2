package com.team2.project.personality.dto;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * P-2 응답 제출. value: 0 매우 A · 1 조금 A · 2 보통 · 3 조금 B · 4 매우 B (API 명세 P-2).
 * 활성 문항마다 정확히 한 번인지는 서비스가 문항 목록과 맞춰 본다
 */
public record PersonalitySubmitRequest(@NotNull List<@NotNull @Valid Answer> answers, JsonNode baseline) {

	/**
	 * 성향카드 재심 요청인지(API 명세 P-2 v0.50, BE-104). 생략하거나 null이면 false.
	 * 불리언이 아니면 400 — 문자열 "true"나 숫자 1을 true로 바꿔 받지 않으려고 JsonNode로 받아 직접 본다
	 */
	public boolean baselineRequested() {
		if (baseline == null || baseline.isNull()) {
			return false;
		}
		if (!baseline.isBoolean()) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "baseline");
		}
		return baseline.asBoolean();
	}

	public record Answer(@NotNull Long questionId, @NotNull @Min(0) @Max(4) Integer value) {
	}
}
