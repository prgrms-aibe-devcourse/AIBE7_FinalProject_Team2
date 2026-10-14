package com.team2.project.personality.domain;

/**
 * 한 체험에서 한 가치관 축에 대해 고른 판단 요소 개수 (experience_personality.axis_counts의 값)
 * down = 감경(관대 글자), up = 가중(엄격 글자). 둘 다 0이면 그 축은 자료 없음 (성향 산식 3-3)
 */
public record AxisCount(int down, int up) {

	public AxisCount {
		if (down < 0 || up < 0) {
			throw new IllegalArgumentException("축 개수는 0 이상이어야 한다");
		}
	}

	public static final AxisCount EMPTY = new AxisCount(0, 0);

	/** 그 축에 고른 요소가 있는지 */
	public boolean hasData() {
		return down + up > 0;
	}
}
