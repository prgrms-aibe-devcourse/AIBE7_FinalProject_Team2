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

	@Test
	void of_sameTagOnBothDirections_keepsOnlyTheFirstOne() {
		// 한 사건의 여러 요소가 같은 태그를 가질 수 있다. 하나를 ↑, 다른 하나를 ↓로 고르면
		// "범행 경위를 무겁게 보고 범행 경위를 감안한 판단"처럼 스스로 모순되는 문장이 된다
		String sentence = UserSummarySentence.of(List.of(
			factor(Direction.UP, "범행 경위"),
			factor(Direction.DOWN, "범행 경위")));

		assertThat(sentence).isEqualTo("범행 경위를 무겁게 본 판단");
	}

	@Test
	void of_sameTagOnBothDirections_doesNotDropOtherTags() {
		String sentence = UserSummarySentence.of(List.of(
			factor(Direction.UP, "범행 경위"),
			factor(Direction.DOWN, "범행 경위"),
			factor(Direction.DOWN, "반성")));

		assertThat(sentence).isEqualTo("범행 경위를 무겁게 보고 반성을 감안한 판단");
	}

	@Test
	void of_tagEndingWithBracket_usesParticleOfLastHangul() {
		// "반성(자백)" → 괄호를 건너뛰고 "백"의 받침으로 판정한다
		assertThat(UserSummarySentence.of(List.of(factor(Direction.DOWN, "반성(자백)"))))
			.isEqualTo("반성(자백)을 감안한 판단");
	}

	@Test
	void of_moreThanThreeTagsPerDirection_keepsOnlyFirstThree() {
		// API 명세 6장 #7: 방향별 태그 최대 3개
		String sentence = UserSummarySentence.of(List.of(
			factor(Direction.UP, "피해 규모"),
			factor(Direction.UP, "범행 방식"),
			factor(Direction.UP, "흉기 사용"),
			factor(Direction.UP, "전력")));

		assertThat(sentence).isEqualTo("피해 규모 · 범행 방식 · 흉기 사용을 무겁게 본 판단");
	}

	@Test
	void of_tagEndingWithDigit_usesParticleOfKoreanReading() {
		// "전과 3" → 삼(받침 ㅁ) → 을
		assertThat(UserSummarySentence.of(List.of(factor(Direction.UP, "전과 3"))))
			.isEqualTo("전과 3을 무겁게 본 판단");
		// "피해자 2" → 이(받침 없음) → 를
		assertThat(UserSummarySentence.of(List.of(factor(Direction.UP, "피해자 2"))))
			.isEqualTo("피해자 2를 무겁게 본 판단");
	}
}
