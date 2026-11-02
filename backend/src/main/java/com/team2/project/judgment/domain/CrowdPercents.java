package com.team2.project.judgment.domain;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * 독자 첫인상 분포의 구간별 비율 (API 14 preToFinal.crowd, FE-34)
 * 반올림하면 합이 99나 101이 될 수 있어, 합이 100이 되도록 소수점이 큰 구간부터 1씩 나눠 준다(최대 잔여법).
 */
public final class CrowdPercents {

	private static final int TOTAL_PERCENT = 100;

	private CrowdPercents() { }

	/** 구간별 선택 수 → 구간별 정수 비율(합 100). 선택이 하나도 없으면 전부 0이다. */
	public static int[] of(long[] counts) {
		long total = Arrays.stream(counts).sum();
		int[] percents = new int[counts.length];
		if (total == 0) {
			return percents;
		}
		long[] remainders = new long[counts.length];
		int assigned = 0;
		for (int i = 0; i < counts.length; i++) {
			percents[i] = (int) (counts[i] * TOTAL_PERCENT / total);
			remainders[i] = counts[i] * TOTAL_PERCENT % total;
			assigned += percents[i];
		}
		// 남은 몫은 나머지가 큰 순서로, 같으면 앞 구간부터 1씩
		int[] order = IntStream.range(0, counts.length).boxed()
			.sorted(Comparator.comparingLong((Integer i) -> remainders[i]).reversed())
			.mapToInt(Integer::intValue).toArray();
		for (int k = 0; k < TOTAL_PERCENT - assigned; k++) {
			percents[order[k]]++;
		}
		return percents;
	}
}
