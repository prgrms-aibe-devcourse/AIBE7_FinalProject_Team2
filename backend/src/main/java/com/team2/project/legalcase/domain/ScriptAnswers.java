package com.team2.project.legalcase.domain;

import java.util.regex.Pattern;

/**
 * 신문 답 규칙 (BE-126). 피고인 역 답을 최소 응답으로 줄이고("…네." · "…아닙니다."), 혐의 인정 여부({@code admits})를 읽는다.
 *
 * <p><b>정본은 파이썬이다</b>(`script_assembler.normalize_answer` · {@code admits}). 공용 계약 파일
 * (`tools/ai-judgment/contracts/script_blocks.json`)의 사례를 양쪽 테스트가 함께 읽어 맞춘다. 자바로 옮길 때 갈리는 곳:
 * <ul>
 *   <li>공백은 유니코드 기준이다 — 전각 공백(U+3000)도 벗긴다. 자바 정규식 {@code \s}는 기본이 ASCII라
 *       {@link Pattern#UNICODE_CHARACTER_CLASS}를 켠다({@code String.trim()}도 전각 공백을 못 벗긴다)</li>
 *   <li>예 · 네 · 아니요는 <b>한 낱말일 때만</b> 답으로 읽는다 — "예전에는" · "네가" · "아니었습니다"는 답이 아니다</li>
 * </ul>
 */
public final class ScriptAnswers {

	public static final String YES = "…네.";
	public static final String NO = "…아닙니다.";

	private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS;
	/** 앞의 공백 · 말줄임표 · 마침표(섞여 있어도 한 번에) */
	private static final Pattern LEAD = Pattern.compile("^[\\s….]+", FLAGS);
	private static final Pattern EDGE_SPACE = Pattern.compile("^\\s+|\\s+$", FLAGS);
	/** 답 낱말 뒤의 경계: 끝 · 공백 · 쉼표 · 마침표 · 물음표 · 느낌표 · 말줄임표 */
	private static final String END = "(?=$|[\\s,.!?…])";
	private static final Pattern YES_WORD = Pattern.compile("^(?:예|네)" + END, FLAGS);
	private static final Pattern NO_WORD = Pattern.compile("^(?:아니요|아니오|아닙니다|아니)" + END, FLAGS);

	private ScriptAnswers() {
	}

	/** 예 · 네 → "…네.", 아니요 · 아닙니다 → "…아닙니다.", 그 밖의 답은 앞에 "…"만 붙인다 */
	public static String normalize(String answer) {
		String stripped = strip(LEAD.matcher(strip(answer == null ? "" : answer)).replaceFirst(""));
		if (YES_WORD.matcher(stripped).find()) {
			return YES;
		}
		if (NO_WORD.matcher(stripped).find()) {
			return NO;
		}
		return "…" + stripped;
	}

	/** 혐의를 인정하는 답인지 = 정규화한 답이 "…네."인지 */
	public static boolean admits(String answer) {
		return YES.equals(normalize(answer));
	}

	private static String strip(String value) {
		return EDGE_SPACE.matcher(value).replaceAll("");
	}
}
