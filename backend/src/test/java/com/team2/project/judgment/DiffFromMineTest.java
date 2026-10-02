package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.dto.AiJudgmentResponse.DiffFromMine;
import com.team2.project.legalcase.domain.PenaltyType;
import org.junit.jupiter.api.Test;

/** 내 판결과의 차이 (API 10 diffFromMine). 형벌 종류는 최종 선고 형벌로 비교한다 */
class DiffFromMineTest {

	private static Judgment prison(PenaltyType finalType, Integer prisonMonths) {
		Judgment judgment = mock(Judgment.class);
		when(judgment.getFinalPenaltyType()).thenReturn(finalType);
		when(judgment.getPrisonMonths()).thenReturn(prisonMonths);
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
		DiffFromMine diff = DiffFromMine.between(prison(PenaltyType.PRISON, 144), prison(PenaltyType.PRISON, 180));

		assertThat(diff.samePenaltyType()).isTrue();
		assertThat(diff.prisonMonthsDiff()).isEqualTo(-36);	// AI가 내 판결보다 36개월 짧다
		assertThat(diff.fineAmountDiff()).isNull();
	}

	@Test
	void between_sameFine_subtractsAmounts() {
		DiffFromMine diff = DiffFromMine.between(fine(3_000_000L), fine(5_000_000L));

		assertThat(diff.samePenaltyType()).isTrue();
		assertThat(diff.fineAmountDiff()).isEqualTo(-2_000_000L);
		assertThat(diff.prisonMonthsDiff()).isNull();
	}

	@Test
	void between_differentPenaltyType_hasNoNumbers() {
		DiffFromMine diff = DiffFromMine.between(prison(PenaltyType.PRISON, 144), prison(PenaltyType.LIFE, null));

		assertThat(diff.samePenaltyType()).isFalse();
		assertThat(diff.prisonMonthsDiff()).isNull();
		assertThat(diff.fineAmountDiff()).isNull();
	}

	@Test
	void between_bothDeath_isSameTypeWithoutNumbers() {
		DiffFromMine diff = DiffFromMine.between(prison(PenaltyType.DEATH, null), prison(PenaltyType.DEATH, null));

		assertThat(diff.samePenaltyType()).isTrue();
		assertThat(diff.prisonMonthsDiff()).isNull();
		assertThat(diff.fineAmountDiff()).isNull();
	}
}
