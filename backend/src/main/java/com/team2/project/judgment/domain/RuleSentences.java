package com.team2.project.judgment.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 매트릭스로 만드는 공통점 · 차이점 규칙 문장 (API 14 ruleSentences, API 명세 6장 #4)
 * AI를 부르지 않는다. "정답 · 틀렸다 · 이중 잣대" 같은 평가 표현은 쓰지 않는다 (요구사항 11장 톤 원칙, FR-6-3).
 * 문장은 요소 라벨(완전한 서술문)이 아니라 요약어(factor.summary_tag)로 만든다. 라벨을 그대로 인용하면
 * "~다"로 끝나는 서술문 뒤에 조사가 붙어 비문이 되기 때문이다(BE-26 확정, PR #62 리뷰).
 * 분류(ALL_SAME은 방향별 · ONLY_ME_MISSED · DIVERGED)마다 요약어를 모아 한 문장으로 만든다.
 */
public record RuleSentences(List<String> common, List<String> differences) {

	private static final String NO_COMMON = "세 판결이 똑같이 본 판단 요소는 없어요.";
	private static final String NO_DIFFERENCE = "세 판결 사이에 판단이 엇갈린 점은 없어요.";

	public static RuleSentences from(List<MatrixRow> matrix) {
		return new RuleSentences(commonSentences(matrix), differenceSentences(matrix));
	}

	private static List<String> commonSentences(List<MatrixRow> matrix) {
		String up = joinTags(matrix, MatrixCategory.ALL_SAME, Direction.UP);
		String down = joinTags(matrix, MatrixCategory.ALL_SAME, Direction.DOWN);
		if (up.isEmpty() && down.isEmpty()) {
			return List.of(NO_COMMON);
		}
		List<String> parts = new ArrayList<>();
		if (!up.isEmpty()) {
			parts.add(up + KoreanParticle.objectParticle(up) + " 형량을 높이는 요소로");
		}
		if (!down.isEmpty()) {
			parts.add(down + KoreanParticle.objectParticle(down) + " 형량을 낮추는 요소로");
		}
		return List.of("세 판결 모두 " + String.join(", ", parts) + " 봤어요.");
	}

	private static List<String> differenceSentences(List<MatrixRow> matrix) {
		List<String> differences = new ArrayList<>();
		String onlyMeMissed = joinTags(matrix, MatrixCategory.ONLY_ME_MISSED, null);
		if (!onlyMeMissed.isEmpty()) {
			differences.add("AI와 재판부는 " + onlyMeMissed + KoreanParticle.objectParticle(onlyMeMissed)
				+ " 고려했지만, 내 판결에서는 고려하지 않았어요.");
		}
		String diverged = joinTags(matrix, MatrixCategory.DIVERGED, null);
		if (!diverged.isEmpty()) {
			differences.add(diverged + "에 대한 판단이 세 판결 사이에서 엇갈렸어요.");
		}
		if (differences.isEmpty()) {
			differences.add(NO_DIFFERENCE);
		}
		return List.copyOf(differences);
	}

	/**
	 * category(그리고 ALL_SAME이면 direction까지) 일치하는 행의 요약어를 표시 순서대로 모아 이어 붙인다.
	 * ONLY_ME_MISSED · DIVERGED는 direction을 가리지 않으므로 null을 넘긴다.
	 */
	private static String joinTags(List<MatrixRow> matrix, MatrixCategory category, Direction direction) {
		return matrix.stream()
			.filter(row -> row.category() == category)
			.filter(row -> direction == null || row.user() == direction)
			.map(RuleSentences::tagOf)
			.distinct()
			.reduce((a, b) -> a + " · " + b)
			.orElse("");
	}

	/** summary_tag는 DB에서 NOT NULL이지만, 비어 있으면 라벨로 대신한다(방어적) */
	private static String tagOf(MatrixRow row) {
		String tag = row.summaryTag();
		return tag == null || tag.isBlank() ? row.label() : tag;
	}
}
