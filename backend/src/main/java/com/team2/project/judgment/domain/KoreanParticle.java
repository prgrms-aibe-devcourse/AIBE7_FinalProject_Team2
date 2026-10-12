package com.team2.project.judgment.domain;

/**
 * 한글 단어 뒤에 붙는 목적격 조사(을 · 를) 판정 (BE-10 리뷰)
 * 내 판결 한 줄 요약(UserSummarySentence)과 세 판결 비교 규칙 문장(RuleSentences)이 함께 쓴다 (BE-11).
 */
public final class KoreanParticle {

	private static final char HANGUL_FIRST = 0xAC00;
	private static final char HANGUL_LAST = 0xD7A3;
	private static final int JONGSEONG_COUNT = 28;

	/** 숫자를 한글로 읽었을 때 받침이 있는지 (0 영 · 1 일 · 3 삼 · 6 육 · 7 칠 · 8 팔) */
	private static final boolean[] DIGIT_HAS_FINAL_CONSONANT = {
		true, true, false, true, false, false, true, true, true, false
	};

	private KoreanParticle() {
	}

	/**
	 * 받침이 있으면 "을", 없으면 "를".
	 * 문구는 팀이 자유롭게 입력하는 값이라 괄호 · 문장부호로 끝날 수 있다("반성(자백)").
	 * 그래서 끝에서부터 거슬러 올라가 처음 만나는 한글 또는 숫자로 판정한다 (BE-10 리뷰).
	 */
	public static String objectParticle(String word) {
		for (int i = word.length() - 1; i >= 0; i--) {
			char letter = word.charAt(i);
			if (letter >= HANGUL_FIRST && letter <= HANGUL_LAST) {
				return (letter - HANGUL_FIRST) % JONGSEONG_COUNT == 0 ? "를" : "을";
			}
			if (letter >= '0' && letter <= '9') {
				return DIGIT_HAS_FINAL_CONSONANT[letter - '0'] ? "을" : "를";
			}
		}
		return "를";
	}
}
