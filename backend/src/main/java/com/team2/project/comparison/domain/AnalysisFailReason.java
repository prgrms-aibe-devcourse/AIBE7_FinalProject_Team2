package com.team2.project.comparison.domain;

/**
 * (확장) AI 비교 분석 실패 사유 (comparison_analysis.fail_reason). 사용자에게 응답하지 않는다
 */
public enum AnalysisFailReason {
	INVALID_FACTOR,			// 목록 밖 판단 요소
	FORBIDDEN_EXPRESSION,	// 정답 · 옳다/틀렸다 · 이중 잣대 · 점수 표현
	INVALID_FORMAT,			// JSON 형식 오류
	TIMEOUT,
	API_ERROR
}
