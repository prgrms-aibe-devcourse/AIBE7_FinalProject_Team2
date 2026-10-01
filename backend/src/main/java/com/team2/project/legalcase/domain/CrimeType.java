package com.team2.project.legalcase.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 범죄 유형 (legal_case.crime_type, sentence_range_option.crime_type)
 * DB CHECK 제약과 같은 값을 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum CrimeType {
	MURDER("생명범죄"),	// 살인
	FRAUD("재산범죄"),	// 사기
	INJURY("신체범죄");	// 상해

	/** 화면에 보이는 범죄 분류명. DB 컬럼이 아니라 코드 상수로 둔다 (ERD 3-1) */
	private final String categoryLabel;
}
