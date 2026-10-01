package com.team2.project.legalcase.domain;

/**
 * 범죄 유형 (legal_case.crime_type, sentence_range_option.crime_type)
 * DB CHECK 제약과 같은 값을 쓴다.
 */
public enum CrimeType {
	MURDER,	// 살인
	FRAUD,	// 사기
	INJURY	// 상해
}
