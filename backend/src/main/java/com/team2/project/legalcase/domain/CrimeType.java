package com.team2.project.legalcase.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CrimeType {

	MURDER("생명범죄"),
	FRAUD("재산범죄"),
	INJURY("신체범죄");

	/** 화면에 보이는 범죄 분류명. DB 컬럼이 아니라 코드 상수로 둔다 (ERD 3-1). */
	private final String categoryLabel;
}
