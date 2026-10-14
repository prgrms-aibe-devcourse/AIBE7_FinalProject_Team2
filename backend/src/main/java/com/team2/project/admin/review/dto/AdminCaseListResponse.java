package com.team2.project.admin.review.dto;

import com.team2.project.legalcase.domain.CaseStatus;
import com.team2.project.legalcase.domain.CrimeType;
import java.time.OffsetDateTime;
import java.util.List;

/** A-5 검수 사건 목록 (API 명세 8-2) */
public record AdminCaseListResponse(List<Item> items, int page, int size, long totalElements) {

	public record Item(
		Long caseId,
		String title,
		CrimeType crimeType,
		CaseStatus status,
		Pending pending,
		boolean hasPublishedAi,
		boolean hasPublishedCourt,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt,
		OffsetDateTime lastActivityAt) {
	}

	/** 검수 대기: AI · 재판부 후보 수(검수 기록 없고 비공개), 가치관 축이 아직 AUTO인 판단 요소 수 */
	public record Pending(int aiCandidates, int courtCandidates, int axisNeedsReview) {
	}
}
