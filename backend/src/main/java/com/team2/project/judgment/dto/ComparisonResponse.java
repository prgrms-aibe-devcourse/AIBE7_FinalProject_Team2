package com.team2.project.judgment.dto;

import com.team2.project.judgment.domain.MatrixRow;
import com.team2.project.judgment.domain.PreToFinalDirection;
import com.team2.project.judgment.domain.RuleSentences;
import com.team2.project.judgment.domain.SubjectType;
import java.util.List;
import java.util.Map;

/**
 * API 14. 세 판결 비교 (S-09) — `GET /cases/{caseId}/experience/comparison`
 * 사전 판단은 완료 후 이 API에서 처음 내려간다 (독자 의견 표시용).
 */
public record ComparisonResponse(
	PreToFinal preToFinal,
	PreToCourt preToCourt,
	Map<SubjectType, JudgmentView> judgments,
	List<MatrixRow> matrix,
	RuleSentences ruleSentences,
	boolean analysisAvailable
) {
	/** 사전 판단 구간 vs 최종 판결 비교 */
	public record PreToFinal(
		PreJudgmentView preJudgment,
		String finalJudgmentText,
		PreToFinalDirection direction,
		String summaryText
	) { }

	/** 사전 판단 대비 재판부 판결의 방향. SAME은 정확한 형량 일치가 아니라 같은 구간이다. */
	public record PreToCourt(PreToFinalDirection direction) { }

	/** 사전 판단 내용. 완료 후 API 14에서만 응답한다. */
	public record PreJudgmentView(Long rangeOptionId, String label, List<Long> factorIds) { }
}
