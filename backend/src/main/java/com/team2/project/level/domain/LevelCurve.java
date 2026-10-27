package com.team2.project.level.domain;

/**
 * 레벨 곡선 (BE-106). 누적 XP(L) = 10000 × [1 − ln(1 + k(100−L)) / ln(1 + 99k)], k = 0.08.
 * 레벨이 XP의 로그 모양이라 초반에 빨리 오르고 뒤로 갈수록 느려진다. Lv1 = 0 XP, Lv100 = 10000 XP.
 * 순수 계산이라 DB 없이 테스트한다
 */
public final class LevelCurve {

	public static final int MAX_LEVEL = 100;
	public static final int TOTAL_XP = 10_000;
	private static final double K = 0.08;

	/** 레벨별 누적 XP. 인덱스 = 레벨 (0번은 쓰지 않는다) */
	private static final int[] XP_AT = new int[MAX_LEVEL + 1];

	static {
		double divisor = Math.log(1 + (MAX_LEVEL - 1) * K);
		for (int level = 1; level <= MAX_LEVEL; level++) {
			XP_AT[level] = (int) Math.round(TOTAL_XP * (1 - Math.log(1 + K * (MAX_LEVEL - level)) / divisor));
		}
	}

	private LevelCurve() {
	}

	/** 그 레벨에 닿는 데 필요한 누적 XP */
	public static int xpAt(int level) {
		if (level < 1 || level > MAX_LEVEL) {
			throw new IllegalArgumentException("레벨은 1 ~ " + MAX_LEVEL + "이어야 한다: " + level);
		}
		return XP_AT[level];
	}

	/** 누적 XP로 지금 레벨 (최대 100). 음수는 1로 본다 */
	public static int levelOf(int xp) {
		int level = 1;
		while (level < MAX_LEVEL && XP_AT[level + 1] <= xp) {
			level++;
		}
		return level;
	}
}
