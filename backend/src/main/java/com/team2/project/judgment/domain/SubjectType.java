package com.team2.project.judgment.domain;

/**
 * 판단 주체 (judgment.subject_type). 이후 JURY 등 값만 추가할 수 있다 (DR-1)
 */
public enum SubjectType {
	USER,	// 사용자
	AI,		// AI 판결
	COURT	// 재판부 (실제 판결)
}
