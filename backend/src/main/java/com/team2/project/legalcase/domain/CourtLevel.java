package com.team2.project.legalcase.domain;

/**
 * 판결 심급 (case_source.court_level)
 * DB CHECK 제약과 같은 값을 쓴다.
 */
public enum CourtLevel {
	FIRST,		// 1심
	APPEAL,		// 항소심
	SUPREME		// 상고심
}
