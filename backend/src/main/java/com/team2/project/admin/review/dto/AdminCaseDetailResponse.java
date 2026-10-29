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
	PublishReadinessResponse publishReadiness,
	ScriptSummary script) {

	/**
	 * 사건 대본 요약 (BE-108, FE 요청 2026-10-27). 공개 준비 화면이 <b>경고로</b> 쓰는 값이다.
	 *
	 * <p>일부러 {@code publishReadiness.reasons}에 넣지 않았다 — 그 목록은 "공개 불가 사유"로 읽히는데,
	 * 미승인 대본은 사건 공개를 막지 않는다(대본은 사건 내용이 아니라 체험 연출이다).
	 *
	 * @param exists       대본이 적재돼 있는지. false면 나머지 두 값은 null이다(기존 사건은 모두 이 경우)
	 * @param reviewStatus 검수 상태(A-13-10과 같은 값). 기록이 없으면 PENDING
	 * @param publishable  파이프라인이 공개 가능하다고 본 결과. false면 승인 자체가 막힌다
	 */
	public record ScriptSummary(boolean exists, ReviewStatus reviewStatus, Boolean publishable) {

		public static final ScriptSummary NONE = new ScriptSummary(false, null, null);
	}

	/**
	 * @param castRoles      배역 {@code {prosecutor, defense}} (BE-125). 배정 전이면 null
	 * @param sensitiveFlags 민감 플래그 전체 (BE-125). 공개 API와 달리 가드 전용 플래그도 보인다. null이면 분류하지
	 *                       않은 사건, []면 분류했고 켜진 것이 없는 사건
	 */
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
		List<String> deidentifiedItems,
		Map<String, String> castRoles,
		List<String> sensitiveFlags) {
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
