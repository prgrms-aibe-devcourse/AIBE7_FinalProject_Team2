package com.team2.project.judgment.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 내 판결 한 줄 요약 (API 명세 판결 응답 공통 형식, REQ-109)
 * 사용자가 고른 판단 요소의 요약 태그(factor.summary_tag)를 방향별로 모아 규칙 문장을 만든다. AI를 부르지 않는다.
 * USER의 summary는 DB에 저장하지 않고(ERD judgment.summary) 응답할 때마다 여기서 만든다.
 * "정답 · 틀렸다" 같은 평가 표현은 쓰지 않는다 (요구사항 11장 톤 원칙).
 */
public final class UserSummarySentence {

	/** 요약 태그 사이 구분자 */
	private static final String TAG_DELIMITER = " · ";

	/** 방향별로 요약에 담는 태그 최대 개수 (API 명세 6장 #7) */
	private static final int MAX_TAGS_PER_DIRECTION = 3;

	/** 고른 요소가 하나도 없을 때 */
	private static final String NO_FACTOR = "판단 요소를 고르지 않은 판단";

	private static final char HANGUL_FIRST = 0xAC00;
	private static final char HANGUL_LAST = 0xD7A3;
	private static final int JONGSEONG_COUNT = 28;

	/** 숫자를 한글로 읽었을 때 받침이 있는지 (0 영 · 1 일 · 3 삼 · 6 육 · 7 칠 · 8 팔) */
	private static final boolean[] DIGIT_HAS_FINAL_CONSONANT = {
		true, true, false, true, false, false, true, true, true, false
	};

	private UserSummarySentence() {
	}

	/**
	 * 최종 판결의 판단 요소 기록으로 한 줄 요약을 만든다. 요소 순서는 넘어온 순서(표시 순서)를 그대로 쓴다.
	 * 같은 태그는 한 번만 쓴다. 한 사건 안의 여러 요소가 같은 태그를 가질 수 있고(ERD), 그중 하나를 ↑로
	 * 다른 하나를 ↓로 고를 수도 있다. 그때는 **먼저 고른(표시 순서가 앞선) 방향에만** 남겨
	 * "A를 무겁게 보고 A를 감안한 판단"처럼 스스로 모순되는 문장이 나오지 않게 한다 (BE-10 리뷰).
	 * 방향별 태그는 최대 {@value #MAX_TAGS_PER_DIRECTION}개까지만 담는다(API 명세 6장 #7).
	 */
	public static String of(List<JudgmentFactor> factors) {
		List<String> up = new ArrayList<>();
		List<String> down = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (JudgmentFactor judgmentFactor : factors) {
			String tag = judgmentFactor.getFactor().getSummaryTag();
			if (tag == null || tag.isBlank() || !seen.add(tag)) {
				continue;
			}
			if (judgmentFactor.getDirection() == Direction.UP && up.size() < MAX_TAGS_PER_DIRECTION) {
				up.add(tag);
			} else if (judgmentFactor.getDirection() == Direction.DOWN && down.size() < MAX_TAGS_PER_DIRECTION) {
				down.add(tag);
			}
		}
		return sentence(String.join(TAG_DELIMITER, up), String.join(TAG_DELIMITER, down));
	}

	private static String sentence(String up, String down) {
		if (up.isEmpty() && down.isEmpty()) {
			return NO_FACTOR;
		}
		if (down.isEmpty()) {
			return up + objectParticle(up) + " 무겁게 본 판단";
		}
		if (up.isEmpty()) {
			return down + objectParticle(down) + " 감안한 판단";
		}
		return up + objectParticle(up) + " 무겁게 보고 " + down + objectParticle(down) + " 감안한 판단";
	}

	/**
	 * 받침이 있으면 "을", 없으면 "를".
	 * 요약 태그는 팀이 자유롭게 입력하는 값이라 괄호 · 문장부호로 끝날 수 있다("반성(자백)").
	 * 그래서 끝에서부터 거슬러 올라가 처음 만나는 한글 또는 숫자로 판정한다 (BE-10 리뷰).
	 */
	private static String objectParticle(String word) {
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
