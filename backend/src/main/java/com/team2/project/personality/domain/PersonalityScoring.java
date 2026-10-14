package com.team2.project.personality.domain;

import com.team2.project.legalcase.domain.ValueAxis;
import java.math.BigInteger;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 판결 성향 계산 (docs/personality-scoring.md 2 · 3 · 4장). DB · 스프링 없이 값만 받아 계산한다.
 * <p>화면용 축 점수는 0 ~ 1(관대 쪽 비중)이고, 글자는 그 소수가 아니라 정수 · 정확한 분수의 부호로 정한다(산식 7장).
 * 0.5 근처에서 실수 오차로 글자가 바뀌지 않게 하기 위해서다. 동점(0)은 관대 글자.
 */
public final class PersonalityScoring {

	private PersonalityScoring() {
	}

	/** 테스트 답 하나: 고른 선택지의 축 · 글자(보통이면 null) · 점수(0 · 1 · 2) */
	public record Pick(ValueAxis axis, String letter, int score) {

		public Pick {
			if (axis == null || score < 0 || score > 2 || (letter == null) != (score == 0)) {
				throw new IllegalArgumentException("선택지 값이 올바르지 않다");
			}
		}

		public static Pick of(PersonalityChoice choice) {
			return new Pick(choice.getAxis(), choice.getLetter(), choice.getScore());
		}
	}

	/** 계산 결과: 4글자 코드(내부용) · 축 점수(0 ~ 1, 자료 없는 축은 null) */
	public record Score(String typeCode, Map<ValueAxis, Double> axisScores) {
	}

	/**
	 * 성향 테스트 채점 (산식 2-3). 활성 문항마다 답이 하나씩 들어온다고 보고, 축마다 문항 수 n은 그 축의 답 개수다.
	 * 축 점수 = (L − S + 2n) ÷ 4n, 글자는 L − S의 부호.
	 */
	public static Score scoreTest(Collection<Pick> picks) {
		Map<ValueAxis, int[]> sums = new EnumMap<>(ValueAxis.class);	// [n, L − S]
		for (Pick pick : picks) {
			int[] sum = sums.computeIfAbsent(pick.axis(), axis -> new int[2]);
			sum[0]++;
			if (pick.letter() == null) {
				continue;
			}
			if (pick.letter().equals(pick.axis().lenientLetter())) {
				sum[1] += pick.score();
			} else if (pick.letter().equals(pick.axis().strictLetter())) {
				sum[1] -= pick.score();
			} else {
				throw new IllegalArgumentException("축에 없는 글자: " + pick.axis() + " " + pick.letter());
			}
		}
		StringBuilder code = new StringBuilder(4);
		Map<ValueAxis, Double> scores = new EnumMap<>(ValueAxis.class);
		for (ValueAxis axis : ValueAxis.values()) {
			int[] sum = sums.get(axis);
			if (sum == null) {
				throw new IllegalArgumentException("답이 없는 축: " + axis);	// 콘텐츠 시드가 축마다 문항을 둔다(산식 2-5)
			}
			int n = sum[0];
			scores.put(axis, (sum[1] + 2.0 * n) / (4.0 * n));
			code.append(axis.letterOf(Integer.signum(sum[1])));
		}
		return new Score(code.toString(), scores);
	}

	/** 체험 하나의 축 점수 = 감경 ÷ (감경 + 가중). 그 축 요소를 고르지 않았으면 null (산식 3-3) */
	public static Double experienceScore(AxisCount count) {
		return count.hasData() ? (double) count.down() / (count.down() + count.up()) : null;
	}

	/** 체험 하나의 축별 점수 (성향 변화 흐름용). 네 축 모두 키가 있고 자료 없는 축은 null */
	public static Map<ValueAxis, Double> experienceScores(Map<ValueAxis, AxisCount> counts) {
		Map<ValueAxis, Double> scores = new EnumMap<>(ValueAxis.class);
		for (ValueAxis axis : ValueAxis.values()) {
			scores.put(axis, experienceScore(counts.getOrDefault(axis, AxisCount.EMPTY)));
		}
		return scores;
	}

	/**
	 * 종합 판결 성향 (산식 4장). 축마다 체험별 축 점수의 평균이고, 자료 없는 체험은 그 축에서 뺀다.
	 * 글자는 Σ (downᵢ − upᵢ) / (downᵢ + upᵢ)의 부호를 정확한 분수로 본다. 네 축 모두 값이 있을 때만 코드가 있다.
	 * 체험이 없거나 네 축 모두 자료가 없으면 비어 있다("종합 없음").
	 */
	public static Optional<Score> overall(List<Map<ValueAxis, AxisCount>> experiences) {
		Map<ValueAxis, Double> scores = new EnumMap<>(ValueAxis.class);
		StringBuilder code = new StringBuilder(4);
		int axesWithData = 0;
		for (ValueAxis axis : ValueAxis.values()) {
			BigInteger numerator = BigInteger.ZERO;	// Σ (down − up) / (down + up)를 분자 / 분모로 쌓는다
			BigInteger denominator = BigInteger.ONE;
			double total = 0;
			int counted = 0;
			for (Map<ValueAxis, AxisCount> counts : experiences) {
				AxisCount count = counts.getOrDefault(axis, AxisCount.EMPTY);
				if (!count.hasData()) {
					continue;
				}
				BigInteger size = BigInteger.valueOf(count.down() + count.up());
				numerator = numerator.multiply(size).add(BigInteger.valueOf(count.down() - count.up()).multiply(denominator));
				denominator = denominator.multiply(size);
				total += experienceScore(count);
				counted++;
			}
			if (counted == 0) {
				scores.put(axis, null);	// 체험 자료 없음 — 테스트 글자로 채우지 않는다(산식 4장)
				continue;
			}
			axesWithData++;
			scores.put(axis, total / counted);
			code.append(axis.letterOf(numerator.signum()));	// 분모는 늘 양수라 분자의 부호가 합의 부호다
		}
		if (axesWithData == 0) {
			return Optional.empty();
		}
		String typeCode = axesWithData == ValueAxis.values().length ? code.toString() : null;
		return Optional.of(new Score(typeCode, scores));
	}
}
