package com.team2.project.judgment.domain;

/**
 * (확장) 판결 후보 검수 상태. ai_generation.review_status는 셋 다, judgment.review_status(V16)는 APPROVED · REJECTED만 저장한다
 * (judgment는 NULL = 대기, Judgment.reviewState())
 */
public enum ReviewStatus {
	PENDING,
	APPROVED,
	REJECTED
}
