package com.team2.project.legalcase.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 범죄 유형 (legal_case.crime_type, sentence_range_option.crime_type)
 * DB CHECK 제약과 같은 값을 쓴다.
 *
 * <p>값을 추가할 때는 마이그레이션도 함께 만든다 (예: V31 강도 · 절도):
 * 범죄 유형 CHECK 4곳(sentence_range_option · legal_case · rag_precedent · case_import)을 넓히고,
 * 사전 판단 형량 구간(sentence_range_option)을 넣는다. CHECK를 빠뜨리면 DbConstraintTest가 실패한다.
 * 사건 목록 summary.byCrimeType은 values()로 만들어 새 값이 자동으로 응답에 추가된다(API 명세 · 화면 필터 칩 확인).
 */
@Getter
@RequiredArgsConstructor
public enum CrimeType {
	MURDER("생명범죄"),	// 살인
	FRAUD("재산범죄"),	// 사기
	INJURY("신체범죄"),	// 상해 (상해치사 · 폭행치사 포함)
	ROBBERY("재산범죄"),	// 강도 (강도치상 · 강도상해 포함, V31)
	THEFT("재산범죄");	// 절도 (특수절도 · 특가법 절도 포함, V31)

	/** 화면에 보이는 범죄 분류명. DB 컬럼이 아니라 코드 상수로 둔다 (ERD 3-1) */
	private final String categoryLabel;
}
