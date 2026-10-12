package com.team2.project.judgment.domain;

/** 세 판결 비교 매트릭스 분류 태그 (API 14 matrix.category, FR-6-4) */
public enum MatrixCategory {
	ALL_SAME,			// 셋 모두 같게 본 요소
	ONLY_ME_MISSED,		// 나만 고려하지 않은 요소 (AI와 재판부는 같은 방향)
	DIVERGED			// 그 밖 (판단이 엇갈린 요소)
}
