package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team2.project.judgment.domain.FinalJudgmentText;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.legalcase.domain.PenaltyType;
import org.junit.jupiter.api.Test;

/** 최종 판결 한 줄 문구 (API 14 preToFinal.finalJudgmentText) */
class FinalJudgmentTextTest {

	private static Judgment judgment(PenaltyType finalType, Integer prisonMonths, Long fineAmount,
		Integer suspensionMonths) {
		Judgment judgment = mock(Judgment.class);
		when(judgment.getFinalPenaltyType()).thenReturn(finalType);
		when(judgment.getPrisonMonths()).thenReturn(prisonMonths);
		when(judgment.getFineAmount()).thenReturn(fineAmount);
		when(judgment.getSuspensionMonths()).thenReturn(suspensionMonths);
		when(judgment.isSuspended()).thenReturn(suspensionMonths != null);
		return judgment;
	}

	@Test
	void of_prison_formatsYears() {
		// ERD 6장 예시: 내 최종 판결 180개월 → "징역 15년"
		assertThat(FinalJudgmentText.of(judgment(PenaltyType.PRISON, 180, null, null))).isEqualTo("징역 15년");
	}

	@Test
	void of_suspendedPrison_addsSuspensionPeriod() {
		assertThat(FinalJudgmentText.of(judgment(PenaltyType.PRISON, 24, null, 36)))
			.isEqualTo("징역 2년에 집행유예 3년");
	}

	@Test
	void of_fine_formatsAmount() {
		assertThat(FinalJudgmentText.of(judgment(PenaltyType.FINE, null, 5_000_000L, null)))
			.isEqualTo("벌금 500만 원");
	}

	@Test
	void of_life_hasNoNumber() {
		assertThat(FinalJudgmentText.of(judgment(PenaltyType.LIFE, null, null, null))).isEqualTo("무기징역");
	}

	@Test
	void of_death_hasNoNumber() {
		assertThat(FinalJudgmentText.of(judgment(PenaltyType.DEATH, null, null, null))).isEqualTo("사형");
	}
}
