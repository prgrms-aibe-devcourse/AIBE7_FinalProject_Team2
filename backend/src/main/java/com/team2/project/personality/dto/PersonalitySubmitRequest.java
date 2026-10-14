package com.team2.project.personality.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * P-2 응답 제출. value: 0 매우 A · 1 조금 A · 2 보통 · 3 조금 B · 4 매우 B (API 명세 P-2).
 * 활성 문항마다 정확히 한 번인지는 서비스가 문항 목록과 맞춰 본다
 */
public record PersonalitySubmitRequest(@NotNull List<@NotNull @Valid Answer> answers) {

	public record Answer(@NotNull Long questionId, @NotNull @Min(0) @Max(4) Integer value) {
	}
}
