package com.team2.project.legalcase.domain;

/**
 * 판단 요소 가치관 축의 후검수 상태 (factor.value_axis_status)
 * DB CHECK 제약과 같은 값을 쓴다.
 */
public enum ValueAxisStatus {
	AUTO,		// 기본값 (AI 투표 · 사람 초안). 아직 관리자가 확정하지 않음
	CONFIRMED	// 관리자가 확정함 (NULL로 확정한 것도 포함). 시드 · 적재 SQL이 덮어쓰지 않는다
}
