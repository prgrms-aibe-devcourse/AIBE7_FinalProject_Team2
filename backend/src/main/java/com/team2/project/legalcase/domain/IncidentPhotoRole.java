package com.team2.project.legalcase.domain;

/**
 * 사건 발단 · 결론 사진의 역할 (BE-78 2차 검토, BE-101). 사건마다 역할별로 1장만 둔다
 * ({@code uk_case_incident_photo_case_role}). 같은 역할로 다시 올리면 기존 행을 교체한다.
 *
 * {@link #legacyDisplayOrder()}는 1단계 동안 함께 유지하는 {@code display_order}를 역할로부터
 * 정하는 값이다(ORIGIN = 0, CONCLUSION = 1) — 더 이상 "그 사건 사진 개수"로 채번하지 않는다
 * (결정: docs/decisions/BE-78.md "확정 사항").
 */
public enum IncidentPhotoRole {
	ORIGIN,
	CONCLUSION;

	public int legacyDisplayOrder() {
		return this == ORIGIN ? 0 : 1;
	}
}
