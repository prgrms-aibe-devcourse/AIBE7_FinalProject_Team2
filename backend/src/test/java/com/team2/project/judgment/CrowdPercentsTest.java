package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;

import com.team2.project.judgment.domain.CrowdPercents;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CrowdPercentsTest {

	@Test
	void of_sumsToHundredEvenWhenRoundingWouldNot() {
		// 1/3씩이면 반올림은 33 · 33 · 33 = 99, 남은 1은 앞 구간부터 준다
		int[] percents = CrowdPercents.of(new long[] {1, 1, 1});

		assertThat(percents).containsExactly(34, 33, 33);
		assertThat(Arrays.stream(percents).sum()).isEqualTo(100);
	}

	@Test
	void of_givesLeftoverToLargestRemainder() {
		// 1/3 · 2/3 → 33.3 · 66.7 → 66에 1을 더해 67
		assertThat(CrowdPercents.of(new long[] {1, 0, 2})).containsExactly(33, 0, 67);
	}

	@Test
	void of_singleChoiceIsHundredAndZeroCountStaysZero() {
		assertThat(CrowdPercents.of(new long[] {0, 5, 0})).containsExactly(0, 100, 0);
	}

	@Test
	void of_noChoicesIsAllZero() {
		assertThat(CrowdPercents.of(new long[] {0, 0})).containsExactly(0, 0);
	}
}
