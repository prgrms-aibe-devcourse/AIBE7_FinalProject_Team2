package com.team2.project.admin.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A-13-21 블록 문자열 한 줄 직접 수정 (BE-126 2차, U0 · U1 · U2 · U4).
 *
 * @param path 고칠 자리 — 검수 결과의 {@code path}와 같은 JSON Pointer (예: {@code /questions/0/q}, {@code /lines/4})
 * @param text 새 문장. 말하는 대사 자리면 길이 규칙은 대사 수정(A-13-13)과 같다({@link CaseScriptLineRequest#lengthViolation()},
 *             자리 판단은 {@code ScriptUnit.isSpoken}, BE-153). 그 밖의 자리는 500자까지
 */
public record CaseScriptTextRequest(@NotBlank @Size(max = 200) String path, @NotBlank @Size(max = 500) String text) {

	public CaseScriptTextRequest {
		text = text == null ? null : text.strip();
	}

	public String lengthViolation() {
		return new CaseScriptLineRequest(text).lengthViolation();
	}
}
