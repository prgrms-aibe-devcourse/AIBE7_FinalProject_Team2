package com.team2.project.admin.review.dto;

import com.team2.project.judgment.domain.ReviewStatus;
import com.team2.project.legalcase.domain.CaseStatus;
import com.team2.project.legalcase.domain.CourtLevel;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.Difficulty;
import com.team2.project.legalcase.domain.RevealStage;
import com.team2.project.legalcase.domain.SummaryTagCandidates;
import com.team2.project.legalcase.domain.ValueAxis;
import com.team2.project.legalcase.domain.ValueAxisStatus;
import com.team2.project.legalcase.domain.ValueAxisVotes;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * A-6 사건 검수 상세 (API 명세 8-2). 원본 판결문 정보는 관리자 API에서만 내려 준다(원문은 A-7).
 * candidates의 키는 AI · COURT
 */
public record AdminCaseDetailResponse(
	Long caseId,
	CaseStatus status,
	Content content,
	List<Source> sources,
	List<FactorItem> factors,
	Map<String, List<Candidate>> candidates,
	PublishReadinessResponse publishReadiness) {

	public record Content(
		String title,
		CrimeType crimeType,
		String chargeName,
		String shortIntro,
		String overview,
		List<String> keywords,
		Difficulty difficulty,
		Integer estimatedMinutes,
		String thumbnailUrl,
		String appliedLaw,
		String statutoryPenaltyText,
		Integer recommendedMinMonths,
		Integer recommendedMaxMonths,
		String recommendedBasis,
		LocalDate incidentDate,
		Guideline guideline,
		List<String> deidentifiedItems) {
	}

	public record Guideline(Long guidelineId, String versionName) {
	}

	public record Source(
		Long sourceId,
		CourtLevel courtLevel,
		String caseNumber,
		String courtName,
		LocalDate decidedAt,
		boolean isFinal,
		String sourceOrg,
		String note,
		boolean hasOriginalText) {
	}

	public record FactorItem(
		Long factorId,
		int displayOrder,
		String label,
		String preLabel,
		RevealStage revealStage,
		String summaryTag,
		ValueAxis valueAxis,
		ValueAxisStatus valueAxisStatus,
		ValueAxisVotes valueAxisVotes,
		SummaryTagCandidates summaryTagCandidates) {
	}

	public record Candidate(
		Long judgmentId,
		ReviewStatus reviewStatus,
		boolean isPublished,
		String penaltyLabel,
		String modelName,
		OffsetDateTime createdAt) {
	}

	public record PublishReadinessResponse(boolean ready, List<String> reasons) {
	}
}
