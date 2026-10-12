package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.PreToFinalDirection;
import com.team2.project.legalcase.domain.PenaltyType;
import com.team2.project.legalcase.domain.RangeKind;
import com.team2.project.legalcase.domain.SentenceRangeOption;
import org.junit.jupiter.api.Test;

/** 사전 판단 구간과 최종 판결 비교 (API 14 preToFinal.direction) */
class PreToFinalDirectionTest {

	private static SentenceRangeOption range(RangeKind kind, Integer min, Integer max) {
		SentenceRangeOption option = mock(SentenceRangeOption.class);
		when(option.getKind()).thenReturn(kind);
		when(option.getMinMonths()).thenReturn(min);
		when(option.getMaxMonths()).thenReturn(max);
		return option;
	}

	private static Judgment finalJudgment(PenaltyType type, PenaltyType reducedTo, Integer prisonMonths,
		Integer suspensionMonths) {
		Judgment judgment = mock(Judgment.class);
		when(judgment.getFinalPenaltyType()).thenReturn(reducedTo != null ? reducedTo : type);
		when(judgment.getPrisonMonths()).thenReturn(prisonMonths);
		when(judgment.isSuspended()).thenReturn(suspensionMonths != null);
		return judgment;
	}

	@Test
	void of_finalAboveRangeUpperBound_isHeavier() {
		// ERD 6장 예시: 구간 "실형 5년 이상 ~ 10년 미만"(60 ~ 120), 최종 180개월(15년) → HEAVIER
		SentenceRangeOption pre = range(RangeKind.PRISON, 60, 120);
		Judgment mine = finalJudgment(PenaltyType.PRISON, null, 180, null);

		assertThat(PreToFinalDirection.of(pre, mine)).isEqualTo(PreToFinalDirection.HEAVIER);
	}

	@Test
	void of_finalBelowRangeLowerBound_isLighter() {
		SentenceRangeOption pre = range(RangeKind.PRISON, 60, 120);
		Judgment mine = finalJudgment(PenaltyType.PRISON, null, 36, null);

		assertThat(PreToFinalDirection.of(pre, mine)).isEqualTo(PreToFinalDirection.LIGHTER);
	}

	@Test
	void of_finalWithinRange_isSame() {
		SentenceRangeOption pre = range(RangeKind.PRISON, 60, 120);
		Judgment mine = finalJudgment(PenaltyType.PRISON, null, 90, null);

		assertThat(PreToFinalDirection.of(pre, mine)).isEqualTo(PreToFinalDirection.SAME);
	}

	@Test
	void of_finalAtUpperBound_isHeavier() {
		// max_months는 미만(상한 제외) 기준이다
		SentenceRangeOption pre = range(RangeKind.PRISON, 60, 120);
		Judgment mine = finalJudgment(PenaltyType.PRISON, null, 120, null);

		assertThat(PreToFinalDirection.of(pre, mine)).isEqualTo(PreToFinalDirection.HEAVIER);
	}

	@Test
	void of_finalKindHeavierThanPreKind_isHeavierRegardlessOfMonths() {
		SentenceRangeOption pre = range(RangeKind.SUSPENDED, null, null);
		Judgment mine = finalJudgment(PenaltyType.PRISON, null, 12, null);

		assertThat(PreToFinalDirection.of(pre, mine)).isEqualTo(PreToFinalDirection.HEAVIER);
	}

	@Test
	void of_finalKindLighterThanPreKind_isLighter() {
		// 구간은 실형인데 최종은 집행유예
		SentenceRangeOption pre = range(RangeKind.PRISON, 36, 60);
		Judgment mine = finalJudgment(PenaltyType.PRISON, null, 24, 36);

		assertThat(PreToFinalDirection.of(pre, mine)).isEqualTo(PreToFinalDirection.LIGHTER);
	}

	@Test
	void of_lifeReducedToPrison_comparesByReducedType() {
		// 무기징역을 감경해 징역으로 선고하면 최종 선고 형벌(reducedTo)로 비교한다
		SentenceRangeOption pre = range(RangeKind.PRISON, 240, null);
		Judgment mine = finalJudgment(PenaltyType.LIFE, PenaltyType.PRISON, 300, null);

		assertThat(PreToFinalDirection.of(pre, mine)).isEqualTo(PreToFinalDirection.SAME);
	}

	@Test
	void of_sameNonPrisonKind_isSame() {
		SentenceRangeOption pre = range(RangeKind.DEATH, null, null);
		Judgment mine = finalJudgment(PenaltyType.DEATH, null, null, null);

		assertThat(PreToFinalDirection.of(pre, mine)).isEqualTo(PreToFinalDirection.SAME);
	}
}
