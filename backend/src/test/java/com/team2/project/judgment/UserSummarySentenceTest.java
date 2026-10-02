package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team2.project.judgment.domain.Direction;
import com.team2.project.judgment.domain.JudgmentFactor;
import com.team2.project.judgment.domain.UserSummarySentence;
import com.team2.project.legalcase.domain.Factor;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 내 판결 한 줄 요약 규칙 문장 (API 명세 판결 응답 공통 형식) */
class UserSummarySentenceTest {

	private static JudgmentFactor factor(Direction direction, String summaryTag) {
		Factor factor = mock(Factor.class);
		when(factor.getSummaryTag()).thenReturn(summaryTag);
		JudgmentFactor judgmentFactor = mock(JudgmentFactor.class);
		when(judgmentFactor.getDirection()).thenReturn(direction);
		when(judgmentFactor.getFactor()).thenReturn(factor);
		return judgmentFactor;
	}

	@Test
	void of_upAndDown_buildsSentenceFromApiSpecExample() {
		String sentence = UserSummarySentence.of(List.of(
			factor(Direction.UP, "피해 규모"),
			factor(Direction.UP, "범행 방식"),
			factor(Direction.DOWN, "반성")));
		assertThat(sentence).isEqualTo("피해 규모 · 범행 방식을 무겁게 보고 반성을 감안한 판단");
	}

	@Test
	void of_onlyUp_saysWeighedHeavily() {
		assertThat(UserSummarySentence.of(List.of(factor(Direction.UP, "범행 방식"))))
			.isEqualTo("범행 방식을 무겁게 본 판단");
	}

	@Test
	void of_onlyDown_saysTakenIntoAccount() {
		assertThat(UserSummarySentence.of(List.of(factor(Direction.DOWN, "반성"))))
			.isEqualTo("반성을 감안한 판단");
	}

	@Test
	void of_noFactor_saysNoneChosen() {
		assertThat(UserSummarySentence.of(List.of())).isEqualTo("판단 요소를 고르지 않은 판단");
	}

	@Test
	void of_sameTagTwice_usesItOnce() {
		String sentence = UserSummarySentence.of(List.of(
			factor(Direction.UP, "피해 규모"),
			factor(Direction.UP, "피해 규모")));
		assertThat(sentence).isEqualTo("피해 규모를 무겁게 본 판단");
	}

	@Test
	void of_tagWithoutFinalConsonant_usesReul() {
		assertThat(UserSummarySentence.of(List.of(factor(Direction.UP, "피해 규모"))))
			.isEqualTo("피해 규모를 무겁게 본 판단");
	}

	@Test
	void of_blankOrMissingTag_isLeftOut() {
		String sentence = UserSummarySentence.of(List.of(
			factor(Direction.UP, "범행 방식"),
			factor(Direction.UP, "  "),
			factor(Direction.UP, null)));
		assertThat(sentence).isEqualTo("범행 방식을 무겁게 본 판단");
	}

	@Test
	void of_everyTagMissing_fallsBackToNoneChosen() {
		assertThat(UserSummarySentence.of(List.of(factor(Direction.UP, null))))
			.isEqualTo("판단 요소를 고르지 않은 판단");
	}
}
