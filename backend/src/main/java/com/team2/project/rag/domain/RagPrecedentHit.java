package com.team2.project.rag.domain;

import java.time.LocalDate;

/**
 * 유사 판례 검색 결과 한 건 (rag_precedent 내부 전용, 사용자 API에 내보내지 않는다).
 * 판결문 원문 · 사건번호 · 법원명은 테이블에 없다. sentencingFactors · flags는 jsonb 원문 문자열이다.
 *
 * @param score 코사인 유사도 (1 - 코사인 거리)
 */
public record RagPrecedentHit(
	long id,
	String caseGroup,
	double score,
	String chargeName,
	String courtLevel,
	LocalDate incidentDate,
	Long guidelineId,
	String penaltyType,
	Integer prisonMonths,
	Integer suspensionMonths,
	Long fineAmount,
	String factsSummary,
	String sentencingReason,
	String sentencingFactors,
	String flags
) {
}
