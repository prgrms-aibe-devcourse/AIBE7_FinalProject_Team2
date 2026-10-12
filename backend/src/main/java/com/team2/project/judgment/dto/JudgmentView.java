package com.team2.project.judgment.dto;

import com.team2.project.judgment.domain.Direction;
import com.team2.project.judgment.domain.ExtraDisposition;
import com.team2.project.judgment.domain.SubjectType;
import com.team2.project.legalcase.domain.PenaltyType;
import java.util.List;

/**
 * 판결 응답 공통 형식 (API 명세 "판결 응답 공통 형식", API 10 · 12 · 14)
 * 사용자 · AI · 재판부 판결을 subjectType 기준의 같은 형식으로 돌려준다 (요구사항 16장 DR-1).
 *
 * 주체에 따라 비어 있는 필드 (API 명세 표)
 * - reasoning: AI · COURT만 (USER는 null)
 * - excerpt · plainExplanation: COURT만 (판결문 발췌 · 쉬운 설명)
 * - factors[].evidence: COURT만 (판결문 근거 문장)
 * - summary: AI · COURT는 팀이 등록한 값, USER는 요약 태그 규칙 문장 (UserSummarySentence)
 *
 * 사건번호 · 법원명 · 선고일 등 원본 판결문 정보는 어떤 주체의 응답에도 넣지 않는다 (FR-5-3).
 */
public record JudgmentView(
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
	List<FactorView> factors
) {
	/** 고려한 판단 요소만 들어간다. 고려하지 않은 요소는 행이 없다 (ERD 결정 #1) */
	public record FactorView(Long factorId, String label, Direction direction, String evidence) { }
}
