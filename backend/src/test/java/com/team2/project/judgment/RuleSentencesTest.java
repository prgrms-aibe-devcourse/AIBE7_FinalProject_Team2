package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;

import com.team2.project.judgment.domain.Direction;
import com.team2.project.judgment.domain.MatrixCategory;
import com.team2.project.judgment.domain.MatrixRow;
import com.team2.project.judgment.domain.RuleSentences;
import com.team2.project.legalcase.domain.RevealStage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 매트릭스로 만드는 공통점 · 차이점 규칙 문장 (API 14 ruleSentences)
 * 문장은 요소 라벨(완전한 서술문)이 아니라 요약어(summaryTag)로 만든다 — 라벨을 그대로 쓰면
 * "~다"로 끝나는 서술문 뒤에 조사가 붙어 비문이 된다(BE-26 확정, PR #62 리뷰).
 */
class RuleSentencesTest {

	private static MatrixRow row(long factorId, String label, String summaryTag, Direction user, Direction ai,
		Direction court, MatrixCategory category) {
		return new MatrixRow(factorId, label, RevealStage.DETAIL, user, ai, court, category, summaryTag);
	}

	@Test
	void from_allSameUp_buildsCommonSentenceWithTagAndHeavierWord() {
		MatrixRow weapon = row(2, "다투던 중 집에 있던 흉기를 집어 들었다", "흉기 사용",
			Direction.UP, Direction.UP, Direction.UP, MatrixCategory.ALL_SAME);

		RuleSentences sentences = RuleSentences.from(List.of(weapon));

		assertThat(sentences.common()).containsExactly("세 판결 모두 흉기 사용을 형량을 높이는 요소로 봤어요.");
		assertThat(sentences.differences()).containsExactly("세 판결 사이에 판단이 엇갈린 점은 없어요.");
	}

	@Test
	void from_allSameDown_usesLighteningWord() {
		MatrixRow remorse = row(7, "수사 초기부터 범행을 인정하고 반성하고 있다", "범행 인정 · 반성",
			Direction.DOWN, Direction.DOWN, Direction.DOWN, MatrixCategory.ALL_SAME);

		RuleSentences sentences = RuleSentences.from(List.of(remorse));

		assertThat(sentences.common()).containsExactly("세 판결 모두 범행 인정 · 반성을 형량을 낮추는 요소로 봤어요.");
	}

	@Test
	void from_allSameBothDirections_combinesIntoOneSentence() {
		MatrixRow up = row(2, "흉기를 집어 들었다", "흉기 사용",
			Direction.UP, Direction.UP, Direction.UP, MatrixCategory.ALL_SAME);
		MatrixRow down = row(7, "반성하고 있다", "범행 인정 · 반성",
			Direction.DOWN, Direction.DOWN, Direction.DOWN, MatrixCategory.ALL_SAME);

		RuleSentences sentences = RuleSentences.from(List.of(up, down));

		assertThat(sentences.common()).containsExactly(
			"세 판결 모두 흉기 사용을 형량을 높이는 요소로, 범행 인정 · 반성을 형량을 낮추는 요소로 봤어요.");
	}

	@Test
	void from_noAllSameRows_fallsBackToNoCommonSentence() {
		RuleSentences sentences = RuleSentences.from(List.of());

		assertThat(sentences.common()).containsExactly("세 판결이 똑같이 본 판단 요소는 없어요.");
	}

	@Test
	void from_onlyMeMissed_buildsDifferenceSentenceNamingAiAndCourt() {
		MatrixRow deposit = row(9, "피해 회복을 위해 5,000만 원을 공탁했다", "피해 회복 공탁",
			null, Direction.DOWN, Direction.DOWN, MatrixCategory.ONLY_ME_MISSED);

		RuleSentences sentences = RuleSentences.from(List.of(deposit));

		assertThat(sentences.differences()).containsExactly(
			"AI와 재판부는 피해 회복 공탁을 고려했지만, 내 판결에서는 고려하지 않았어요.");
	}

	@Test
	void from_diverged_buildsGenericDifferenceSentenceWithTags() {
		MatrixRow children = row(6, "피해자에게는 부양하던 어린 자녀 2명이 있다", "피해자의 부양 가족",
			Direction.UP, null, Direction.UP, MatrixCategory.DIVERGED);

		RuleSentences sentences = RuleSentences.from(List.of(children));

		assertThat(sentences.differences()).containsExactly("피해자의 부양 가족에 대한 판단이 세 판결 사이에서 엇갈렸어요.");
	}

	@Test
	void from_sameTagOnTwoFactorsInSameCategory_mentionsTagOnlyOnce() {
		// 같은 요약어를 가진 요소가 둘 다 DIVERGED면 "A · A에 대한 판단이…"처럼 중복되면 안 된다 (PR #67 리뷰)
		MatrixRow first = row(6, "피해자에게는 부양하던 어린 자녀 2명이 있다", "피해자의 부양 가족",
			Direction.UP, null, Direction.UP, MatrixCategory.DIVERGED);
		MatrixRow second = row(10, "다른 요소지만 같은 요약어", "피해자의 부양 가족",
			Direction.DOWN, Direction.UP, null, MatrixCategory.DIVERGED);

		RuleSentences sentences = RuleSentences.from(List.of(first, second));

		assertThat(sentences.differences()).containsExactly("피해자의 부양 가족에 대한 판단이 세 판결 사이에서 엇갈렸어요.");
	}

	@Test
	void from_onlyMeMissedAndDiverged_bothAppearAsSeparateSentences() {
		MatrixRow onlyMeMissed = row(9, "공탁했다", "피해 회복 공탁",
			null, Direction.DOWN, Direction.DOWN, MatrixCategory.ONLY_ME_MISSED);
		MatrixRow diverged = row(6, "자녀가 있다", "피해자의 부양 가족",
			Direction.UP, null, Direction.UP, MatrixCategory.DIVERGED);

		RuleSentences sentences = RuleSentences.from(List.of(onlyMeMissed, diverged));

		assertThat(sentences.differences()).hasSize(2);
	}

	@Test
	void from_blankSummaryTag_fallsBackToLabel() {
		MatrixRow noTag = row(1, "요약어가 비어 있는 요소", "",
			Direction.UP, Direction.UP, Direction.UP, MatrixCategory.ALL_SAME);

		RuleSentences sentences = RuleSentences.from(List.of(noTag));

		assertThat(sentences.common()).containsExactly("세 판결 모두 요약어가 비어 있는 요소를 형량을 높이는 요소로 봤어요.");
	}
}
