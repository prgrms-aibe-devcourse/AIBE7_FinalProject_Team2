package com.team2.project.judgment.domain;

import com.team2.project.legalcase.domain.Factor;
import java.util.ArrayList;
import java.util.List;

/**
 * 내 판결 한 줄 요약 (API 명세 판결 응답 공통 형식, REQ-109)
 * 사용자가 고른 판단 요소의 요약 태그(factor.summary_tag)를 방향별로 모아 규칙 문장을 만든다. AI를 부르지 않는다.
 * USER의 summary는 DB에 저장하지 않고(ERD judgment.summary) 응답할 때마다 여기서 만든다.
 * "정답 · 틀렸다" 같은 평가 표현은 쓰지 않는다 (요구사항 11장 톤 원칙).
 */
public final class UserSummarySentence {

	/** 요약 태그 사이 구분자 */
	private static final String TAG_DELIMITER = " · ";

	/** 고른 요소가 하나도 없을 때 */
	private static final String NO_FACTOR = "판단 요소를 고르지 않은 판단";

	private static final char HANGUL_FIRST = 0xAC00;
	private static final char HANGUL_LAST = 0xD7A3;
	private static final int JONGSEONG_COUNT = 28;

	private UserSummarySentence() {
	}

	/** 최종 판결의 판단 요소 기록으로 한 줄 요약을 만든다. 요소 순서는 넘어온 순서(표시 순서)를 그대로 쓴다 */
	public static String of(List<JudgmentFactor> factors) {
		String up = joinTags(factors, Direction.UP);
		String down = joinTags(factors, Direction.DOWN);
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

	/** 같은 태그는 한 번만 쓴다. 태그가 없는 요소는 문장에 넣지 않는다 */
	private static String joinTags(List<JudgmentFactor> factors, Direction direction) {
		List<String> tags = new ArrayList<>();
		for (JudgmentFactor judgmentFactor : factors) {
			if (judgmentFactor.getDirection() != direction) {
				continue;
			}
			Factor factor = judgmentFactor.getFactor();
			String tag = factor == null ? null : factor.getSummaryTag();
			if (tag != null && !tag.isBlank() && !tags.contains(tag)) {
				tags.add(tag);
			}
		}
		return String.join(TAG_DELIMITER, tags);
	}

	/** 받침이 있으면 "을", 없으면 "를". 한글이 아닌 글자로 끝나면 "를" */
	private static String objectParticle(String word) {
		char last = word.charAt(word.length() - 1);
		if (last < HANGUL_FIRST || last > HANGUL_LAST) {
			return "를";
		}
		return (last - HANGUL_FIRST) % JONGSEONG_COUNT == 0 ? "를" : "을";
	}
}
