package com.team2.project.admin.review.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A-13-15 대본 블록 재생성 요청 (BE-129 2차).
 *
 * @param units   다시 만들 블록 (U0 ~ U4, 1 ~ 5개, 겹치면 안 된다). 그 블록을 입력으로 받는 뒤 블록 · 없는 앞 블록은 파이프라인이 더한다
 * @param cascade 뒤 블록도 함께 만드는지. 비우면 true(묶음 수정). false면 고른 블록만 — 뒤 블록은 낡음으로 남는다(세부조정)
 */
public record ScriptRegenerationRequest(@NotEmpty @Size(max = 5) List<String> units, Boolean cascade) {

	public boolean cascadeOrDefault() {
		return cascade == null || cascade;
	}
}
