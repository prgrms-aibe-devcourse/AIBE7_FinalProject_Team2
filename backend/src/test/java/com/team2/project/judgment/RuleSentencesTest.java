package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;

import com.team2.project.judgment.domain.Direction;
import com.team2.project.judgment.domain.MatrixCategory;
import com.team2.project.judgment.domain.MatrixRow;
import com.team2.project.judgment.domain.RuleSentences;
import com.team2.project.legalcase.domain.RevealStage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 매트릭스로 만드는 공통점 · 차이점 규칙 문장 (API 14 ruleSentences) */
class RuleSentencesTest {

	private static MatrixRow row(long factorId, String label, Direction user, Direction ai, Direction court,
		MatrixCategory category) {
		return new MatrixRow(factorId, label, RevealStage.DETAIL, user, ai, court, category);
	}

	@Test
	void from_allSame_buildsCommonSentenceWithDirectionWord() {
		MatrixRow allSameUp = row(2, "다투던 중 집에 있던 흉기를 집어 들었다",
			Direction.UP, Direction.UP, Direction.UP, MatrixCategory.ALL_SAME);

		RuleSentences sentences = RuleSentences.from(List.of(allSameUp));

		assertThat(sentences.common()).containsExactly(
			"세 판결 모두 \"다투던 중 집에 있던 흉기를 집어 들었다\"를 형량을 높이는 요소로 봤어요.");
		assertThat(sentences.differences()).isEmpty();
	}

	@Test
	void from_allSameDown_usesLighteningDirectionWord() {
		MatrixRow allSameDown = row(7, "수사 초기부터 범행을 인정하고 반성하고 있다",
			Direction.DOWN, Direction.DOWN, Direction.DOWN, MatrixCategory.ALL_SAME);

		RuleSentences sentences = RuleSentences.from(List.of(allSameDown));

		assertThat(sentences.common()).containsExactly(
			"세 판결 모두 \"수사 초기부터 범행을 인정하고 반성하고 있다\"를 형량을 낮추는 요소로 봤어요.");
	}

	@Test
	void from_onlyMeMissed_buildsDifferenceSentenceNamingAiAndCourt() {
		MatrixRow onlyMeMissed = row(9, "피해 회복을 위해 5,000만 원을 공탁했다",
			null, Direction.DOWN, Direction.DOWN, MatrixCategory.ONLY_ME_MISSED);

		RuleSentences sentences = RuleSentences.from(List.of(onlyMeMissed));

		assertThat(sentences.differences()).containsExactly(
			"AI와 재판부는 \"피해 회복을 위해 5,000만 원을 공탁했다\"를 고려했지만, 내 판결에서는 고려하지 않았어요.");
		assertThat(sentences.common()).isEmpty();
	}

	@Test
	void from_diverged_buildsGenericDifferenceSentence() {
		MatrixRow diverged = row(6, "피해자에게는 부양하던 어린 자녀 2명이 있다",
			Direction.UP, null, Direction.UP, MatrixCategory.DIVERGED);

		RuleSentences sentences = RuleSentences.from(List.of(diverged));

		assertThat(sentences.differences()).containsExactly(
			"\"피해자에게는 부양하던 어린 자녀 2명이 있다\"에 대한 판단이 세 판결 사이에서 엇갈렸어요.");
	}

	@Test
	void from_mixedMatrix_keepsOrderAndSeparatesCommonFromDifferences() {
		List<MatrixRow> matrix = List.of(
			row(2, "흉기를 집어 들었다", Direction.UP, Direction.UP, Direction.UP, MatrixCategory.ALL_SAME),
			row(6, "자녀가 있다", Direction.UP, null, Direction.UP, MatrixCategory.DIVERGED),
			row(9, "공탁했다", null, Direction.DOWN, Direction.DOWN, MatrixCategory.ONLY_ME_MISSED));

		RuleSentences sentences = RuleSentences.from(matrix);

		assertThat(sentences.common()).hasSize(1);
		assertThat(sentences.differences()).hasSize(2);
	}
}
