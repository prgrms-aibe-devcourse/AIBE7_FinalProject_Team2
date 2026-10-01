package com.team2.project.legalcase.domain;

/**
 * 사건 등록 상태 (legal_case.status). PUBLISHED만 사용자에게 노출한다
 * DB CHECK 제약과 같은 값을 쓴다.
 */
public enum CaseStatus {
	DRAFT,
	REVIEW,
	PUBLISHED
}
