package com.team2.project.judgment.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 매트릭스로 만드는 공통점 · 차이점 규칙 문장 (API 14 ruleSentences, API 명세 6장 #4)
 * AI를 부르지 않는다. "정답 · 틀렸다 · 이중 잣대" 같은 평가 표현은 쓰지 않는다 (요구사항 11장 톤 원칙, FR-6-3).
 * 문장 틀은 API 명세가 구현 때 정하도록 남긴 부분이다(BE-11): 요소 문구를 그대로 인용해 어떤 사건에도
 * 안전하게 쓸 수 있는 틀로 정했다.
 */
public record RuleSentences(List<String> common, List<String> differences) {

	public static RuleSentences from(List<MatrixRow> matrix) {
		List<String> common = new ArrayList<>();
		List<String> differences = new ArrayList<>();
		for (MatrixRow row : matrix) {
			switch (row.category()) {
				case ALL_SAME -> common.add(allSameSentence(row));
				case ONLY_ME_MISSED -> differences.add(onlyMeMissedSentence(row));
				case DIVERGED -> differences.add(divergedSentence(row));
			}
		}
		return new RuleSentences(List.copyOf(common), List.copyOf(differences));
	}

	/** ALL_SAME: 셋 다 같은 방향이므로 그중 하나(user)로 방향 문구를 고른다 */
	private static String allSameSentence(MatrixRow row) {
		String directionText = row.user() == Direction.UP ? "형량을 높이는" : "형량을 낮추는";
		return "세 판결 모두 \"" + row.label() + "\"" + KoreanParticle.objectParticle(row.label())
			+ " " + directionText + " 요소로 봤어요.";
	}

	/** ONLY_ME_MISSED: 정의상 AI · 재판부는 항상 같은 방향이다 */
	private static String onlyMeMissedSentence(MatrixRow row) {
		return "AI와 재판부는 \"" + row.label() + "\"" + KoreanParticle.objectParticle(row.label())
			+ " 고려했지만, 내 판결에서는 고려하지 않았어요.";
	}

	/** DIVERGED: 조합이 다양해(user만 고려 · 방향이 서로 다름 등) 공통 틀로만 안내한다 */
	private static String divergedSentence(MatrixRow row) {
		return "\"" + row.label() + "\"에 대한 판단이 세 판결 사이에서 엇갈렸어요.";
	}
}
