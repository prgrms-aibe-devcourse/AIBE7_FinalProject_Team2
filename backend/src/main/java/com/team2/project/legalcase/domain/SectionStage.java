package com.team2.project.legalcase.domain;

/**
 * 사건 정보 섹션 단계 (case_section.stage)
 * DB CHECK 제약과 같은 값을 쓴다.
 */
public enum SectionStage {
	DETAIL,		// ② 상세 사실관계
	ARGUMENT,	// ③ 양측 주장
	LAW,		// ④ 법률 · 양형기준
	SUMMARY		// S-05 최종 정리 요약
}
