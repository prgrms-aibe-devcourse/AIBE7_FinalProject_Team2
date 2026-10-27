package com.team2.project.admin.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A-13-13 대사 한 줄 직접 수정.
 *
 * <p>길이 규칙은 파이프라인의 검증과 <b>같은 방식</b>으로 센다(`script_generation.py` {@code validate_line}) —
 * 빈 줄을 뺀 줄들의 글자 수 합이 100자 이하, 줄은 2줄까지. **줄바꿈 문자는 세지 않는다**: 파이프라인은
 * {@code sum(len(line))}로 세는데 {@code @Size}는 줄바꿈까지 세어, 50자 + 50자 두 줄(파이프라인 기준 합격)이
 * 101자로 거절되는 어긋남이 있었다(PR #300 리뷰 2번).
 *
 * <p>금지어 · 원문에 없는 숫자 같은 의미 검사는 하지 않는다. 그 판단을 사람이 하라고 두는 기능이다.
 */
public record CaseScriptLineRequest(@NotBlank @Size(max = 500) String text) {

	/** 파이프라인 MAX_CHARS · MAX_LINES와 같은 값 */
	private static final int MAX_CHARS = 100;
	private static final int MAX_LINES = 2;

	/** 앞뒤 공백은 잘라서 받는다. 검증 · 길이 · 저장 모두 자른 값 기준 (A-10과 같다) */
	public CaseScriptLineRequest {
		text = text == null ? null : text.strip();
	}

	/**
	 * 길이 규칙을 어겼으면 그 이유, 아니면 {@code null}.
	 *
	 * <p>{@code @Size(max = 500)}은 터무니없는 본문을 Bean Validation 단계에서 먼저 끊으려는 넉넉한 상한이고,
	 * 실제 기준은 여기서 파이프라인과 같은 방식으로 센다.
	 */
	public String lengthViolation() {
		if (text == null) {
			return null;		// @NotBlank가 먼저 걸러낸다
		}
		java.util.List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
		if (lines.size() > MAX_LINES) {
			return "대사는 %d줄까지 쓸 수 있습니다.".formatted(MAX_LINES);
		}
		int chars = lines.stream().mapToInt(String::length).sum();
		if (chars > MAX_CHARS) {
			return "대사는 줄바꿈을 빼고 %d자까지 쓸 수 있습니다. (지금 %d자)".formatted(MAX_CHARS, chars);
		}
		return null;
	}
}
