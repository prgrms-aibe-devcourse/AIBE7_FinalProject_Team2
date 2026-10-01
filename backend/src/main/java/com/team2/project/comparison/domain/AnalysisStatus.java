package com.team2.project.comparison.domain;

/**
 * (확장) AI 비교 분석 생성 상태 (comparison_analysis.status)
 */
public enum AnalysisStatus {
	PENDING,	// 생성 중 (실제 판결 공개 시 만든다)
	DONE,		// 검증 통과, 화면에 표시
	FAILED		// 실패 · 검증 탈락 · 시간 초과 → 규칙 문장 유지
}
