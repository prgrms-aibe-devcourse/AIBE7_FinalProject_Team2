package com.team2.project.legalcase.domain;

/**
 * 형벌 종류 (penalty_rule.penalty_type, judgment.penalty_type, judgment.reduced_to)
 * DB CHECK 제약과 같은 값을 쓴다.
 */
public enum PenaltyType {
	DEATH,	// 사형
	LIFE,	// 무기징역
	PRISON,	// 유기징역
	FINE;	// 벌금

	/** 사형 · 무기처럼 형량(개월 · 금액) 입력이 없는 형벌인지 */
	public boolean isDeathOrLife() {
		return this == DEATH || this == LIFE;
	}

	/** 이 형벌을 고른 뒤 target으로 감경할 수 있는지 (사형 → 무기 · 징역, 무기 → 징역, ERD v1.4) */
	public boolean canReduceTo(PenaltyType target) {
		return switch (this) {
			case DEATH -> target == LIFE || target == PRISON;
			case LIFE -> target == PRISON;
			default -> false;
		};
	}
}
