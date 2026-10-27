package com.team2.project.admin.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A-13-13 대사 한 줄 직접 수정. 길이 규칙은 파이프라인의 검증과 같은 값을 쓴다
 * (`script_generation.py` MAX_LINES = 2 · MAX_CHARS = 100) — 화면에 들어가는 자리가 같기 때문이다.
 *
 * <p>금지어 · 원문에 없는 숫자 같은 의미 검사는 하지 않는다. 그 판단을 사람이 하라고 두는 기능이다.
 */
public record CaseScriptLineRequest(@NotBlank @Size(max = 100) String text) {

	/** 앞뒤 공백은 잘라서 받는다. 검증 · 길이 · 저장 모두 자른 값 기준 (A-10과 같다) */
	public CaseScriptLineRequest {
		text = text == null ? null : text.strip();
	}

	/** 대사는 2줄까지 (파이프라인 MAX_LINES). 빈 줄은 세지 않는다 */
	public boolean tooManyLines() {
		return text != null && text.lines().filter(line -> !line.isBlank()).count() > 2;
	}
}
