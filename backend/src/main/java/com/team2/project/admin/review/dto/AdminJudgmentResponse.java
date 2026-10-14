package com.team2.project.admin.review.dto;

import com.team2.project.judgment.domain.ExtraDisposition;
import com.team2.project.judgment.domain.ReviewStatus;
import com.team2.project.judgment.domain.SubjectType;
import com.team2.project.judgment.dto.JudgmentView;
import com.team2.project.legalcase.domain.PenaltyType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * A-8 판결 후보 상세 (API 명세 8-2). 판결 응답 공통 형식에 참고 자료 태그와 검수 · 생성 정보를 더한다.
 * generation은 AI 후보만 있고 재판부 후보는 null. raw_output(모델 원본 출력)은 내려 주지 않는다
 */
public record AdminJudgmentResponse(
	Long judgmentId,
	Long caseId,
	SubjectType subjectType,
	ReviewStatus reviewStatus,
	boolean isPublished,
	String reviewedBy,
	OffsetDateTime reviewedAt,
	String reviewNote,
	Body judgment,
	Generation generation) {

	/** 판결 응답 공통 형식(JudgmentView)의 필드 + referenceTags */
	public record Body(
		SubjectType subjectType,
		PenaltyType penaltyType,
		PenaltyType reducedTo,
		Integer prisonMonths,
		Long fineAmount,
		Integer suspensionMonths,
		List<ExtraDisposition> extraDispositions,
		String summary,
		String reasoning,
		String excerpt,
		String plainExplanation,
		List<String> referenceTags,
		List<JudgmentView.FactorView> factors) {

		public static Body of(JudgmentView view, List<String> referenceTags) {
			return new Body(view.subjectType(), view.penaltyType(), view.reducedTo(), view.prisonMonths(),
				view.fineAmount(), view.suspensionMonths(), view.extraDispositions(), view.summary(), view.reasoning(),
				view.excerpt(), view.plainExplanation(), referenceTags, view.factors());
		}
	}

	public record Generation(
		String modelName,
		String promptVersion,
		Map<String, Object> inputSnapshot,
		Map<String, Object> generationReport) {
	}
}
