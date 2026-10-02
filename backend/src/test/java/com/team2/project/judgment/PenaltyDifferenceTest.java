package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.PenaltyDifference;
import com.team2.project.legalcase.domain.PenaltyType;
import org.junit.jupiter.api.Test;

/** 두 판결의 형벌 차이 (API 10 diffFromMine). 최종 선고 형벌 + 집행유예 여부로 같은 형벌인지 본다 */
class PenaltyDifferenceTest {

	private static Judgment prison(int prisonMonths, boolean suspended) {
		Judgment judgment = mock(Judgment.class);
		when(judgment.getFinalPenaltyType()).thenReturn(PenaltyType.PRISON);
		when(judgment.getPrisonMonths()).thenReturn(prisonMonths);
		when(judgment.isSuspended()).thenReturn(suspended);
		return judgment;
	}

	private static Judgment noTerm(PenaltyType finalType) {
		Judgment judgment = mock(Judgment.class);
		when(judgment.getFinalPenaltyType()).thenReturn(finalType);
		return judgment;
	}

	private static Judgment fine(long fineAmount) {
		Judgment judgment = mock(Judgment.class);
		when(judgment.getFinalPenaltyType()).thenReturn(PenaltyType.FINE);
		when(judgment.getFineAmount()).thenReturn(fineAmount);
		return judgment;
	}

	@Test
	void between_samePrison_subtractsMineFromTarget() {
		PenaltyDifference diff = PenaltyDifference.between(prison(144, false), prison(180, false));

		assertThat(diff.samePenaltyType()).isTrue();
		assertThat(diff.prisonMonthsDiff()).isEqualTo(-36);	// AI가 내 판결보다 36개월 짧다
		assertThat(diff.fineAmountDiff()).isNull();
	}

	@Test
	void between_sameFine_subtractsAmounts() {
		PenaltyDifference diff = PenaltyDifference.between(fine(3_000_000L), fine(5_000_000L));

		assertThat(diff.samePenaltyType()).isTrue();
		assertThat(diff.fineAmountDiff()).isEqualTo(-2_000_000L);
		assertThat(diff.prisonMonthsDiff()).isNull();
	}

	@Test
	void between_differentPenaltyType_hasNoNumbers() {
		PenaltyDifference diff = PenaltyDifference.between(prison(144, false), noTerm(PenaltyType.LIFE));

		assertThat(diff.samePenaltyType()).isFalse();
		assertThat(diff.prisonMonthsDiff()).isNull();
		assertThat(diff.fineAmountDiff()).isNull();
	}

	@Test
	void between_bothDeath_isSameTypeWithoutNumbers() {
		PenaltyDifference diff = PenaltyDifference.between(noTerm(PenaltyType.DEATH), noTerm(PenaltyType.DEATH));

		assertThat(diff.samePenaltyType()).isTrue();
		assertThat(diff.prisonMonthsDiff()).isNull();
		assertThat(diff.fineAmountDiff()).isNull();
	}

	@Test
	void between_suspendedVersusActual_isNotTheSamePenalty() {
		// 같은 징역 24개월이어도 집행유예와 실형은 다른 판결이다 (형벌 무게 순서 SUSPENDED < PRISON)
		PenaltyDifference diff = PenaltyDifference.between(prison(24, true), prison(24, false));

		assertThat(diff.samePenaltyType()).isFalse();
		assertThat(diff.prisonMonthsDiff()).isNull();
		assertThat(diff.fineAmountDiff()).isNull();
	}

	@Test
	void between_bothSuspended_comparesPrisonMonths() {
		PenaltyDifference diff = PenaltyDifference.between(prison(24, true), prison(36, true));

		assertThat(diff.samePenaltyType()).isTrue();
		assertThat(diff.prisonMonthsDiff()).isEqualTo(-12);
	}
}
