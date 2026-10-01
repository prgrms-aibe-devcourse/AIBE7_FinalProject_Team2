package com.team2.project.legalcase.domain;

/**
 * 사전 판단 형량 구간 종류 (sentence_range_option.kind). 무겁기 순서: FINE < SUSPENDED < PRISON < LIFE < DEATH
 * DB CHECK 제약과 같은 값을 쓴다.
 */
public enum RangeKind {
	FINE,		// 벌금형
	SUSPENDED,	// 징역형 집행유예
	PRISON,		// 실형 (개월 범위로 세분)
	LIFE,		// 무기징역 (살인)
	DEATH		// 사형 (살인)
}
