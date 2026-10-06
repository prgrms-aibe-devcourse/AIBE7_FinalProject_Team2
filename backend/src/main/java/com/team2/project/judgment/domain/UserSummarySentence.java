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

	/** 고른 요소가 하나도 없을 때 */
	private static final String NO_FACTOR = "판단 요소를 고르지 않은 판단";

	private UserSummarySentence() {
	}

	/**
	 * 최종 판결의 판단 요소 기록으로 한 줄 요약을 만든다. 요소 순서는 넘어온 순서(표시 순서)를 그대로 쓴다.
	 * 같은 태그는 한 번만 쓴다. 한 사건 안의 여러 요소가 같은 태그를 가질 수 있고(ERD), 그중 하나를 ↑로
	 * 다른 하나를 ↓로 고를 수도 있다. 그때는 **먼저 고른(표시 순서가 앞선) 방향에만** 남겨
	 * "A를 무겁게 보고 A를 감안한 판단"처럼 스스로 모순되는 문장이 나오지 않게 한다 (BE-10 리뷰).
	 * 방향별 태그 수는 제한하지 않는다 — 요소마다 서로 다른 요약어가 붙으므로 많이 고르면 문장이 길어질 수
	 * 있지만, 우선 그대로 두고 S-09에서 실제로 문제가 되면 상한을 다시 검토한다 (BE-26 확정).
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
			if (judgmentFactor.getDirection() == Direction.UP) {
				up.add(tag);
			} else if (judgmentFactor.getDirection() == Direction.DOWN) {
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
			return up + KoreanParticle.objectParticle(up) + " 무겁게 본 판단";
		}
		if (up.isEmpty()) {
			return down + KoreanParticle.objectParticle(down) + " 감안한 판단";
		}
		return up + KoreanParticle.objectParticle(up) + " 무겁게 보고 "
			+ down + KoreanParticle.objectParticle(down) + " 감안한 판단";
	}
}
