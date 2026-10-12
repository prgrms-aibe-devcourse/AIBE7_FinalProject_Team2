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

	/** 실형 징역 */
	private static Judgment prison(int prisonMonths) {
		return prison(prisonMonths, null);
	}

	/** suspensionMonths가 있으면 집행유예 */
	private static Judgment prison(int prisonMonths, Integer suspensionMonths) {
		Judgment judgment = mock(Judgment.class);
		when(judgment.getFinalPenaltyType()).thenReturn(PenaltyType.PRISON);
		when(judgment.getPrisonMonths()).thenReturn(prisonMonths);
		when(judgment.getSuspensionMonths()).thenReturn(suspensionMonths);
		when(judgment.isSuspended()).thenReturn(suspensionMonths != null);
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
		PenaltyDifference diff = PenaltyDifference.between(prison(144), prison(180));

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
		PenaltyDifference diff = PenaltyDifference.between(prison(144), noTerm(PenaltyType.LIFE));

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
		PenaltyDifference diff = PenaltyDifference.between(prison(24, 36), prison(24));

		assertThat(diff.samePenaltyType()).isFalse();
		assertThat(diff.prisonMonthsDiff()).isNull();
		assertThat(diff.fineAmountDiff()).isNull();
	}

	@Test
	void between_bothSuspended_comparesPrisonMonths() {
		PenaltyDifference diff = PenaltyDifference.between(prison(24, 36), prison(36, 36));

		assertThat(diff.samePenaltyType()).isTrue();
		assertThat(diff.prisonMonthsDiff()).isEqualTo(-12);
		assertThat(diff.suspensionMonthsDiff()).isZero();
	}

	@Test
	void between_bothSuspendedWithDifferentPeriod_reportsSuspensionDiff() {
		// 징역 개월 수가 같아도 유예 기간이 다르면 같은 판결이 아니다
		PenaltyDifference diff = PenaltyDifference.between(prison(24, 36), prison(24, 12));

		assertThat(diff.samePenaltyType()).isTrue();
		assertThat(diff.prisonMonthsDiff()).isZero();
		assertThat(diff.suspensionMonthsDiff()).isEqualTo(24);
	}

	@Test
	void between_neitherSuspended_hasNoSuspensionDiff() {
		assertThat(PenaltyDifference.between(prison(144), prison(180)).suspensionMonthsDiff()).isNull();
	}
}
