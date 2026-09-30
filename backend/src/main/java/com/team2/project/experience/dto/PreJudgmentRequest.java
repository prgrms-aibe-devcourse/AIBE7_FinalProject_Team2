package com.team2.project.experience.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** API 5 요청. factorIds는 확장 기능이라 MVP는 빈 배열이거나 생략한다. */
public record PreJudgmentRequest(@NotNull Long rangeOptionId, List<@NotNull Long> factorIds) {

	public List<Long> factorIdsOrEmpty() {
		return factorIds == null ? List.of() : factorIds;
	}
}
