package com.team2.project.judgment.dto;

import com.team2.project.judgment.domain.MatrixRow;
import com.team2.project.judgment.domain.PreToFinalDirection;
import com.team2.project.judgment.domain.RuleSentences;
import com.team2.project.judgment.domain.SubjectType;
import java.util.List;
import java.util.Map;

/**
 * API 14. 세 판결 비교 (S-09) — `GET /cases/{caseId}/experience/comparison`
 * 사전 판단은 이 API에서 처음 내려간다 (API 명세).
 */
public record ComparisonResponse(
	PreToFinal preToFinal,
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

	/** 사전 판단 내용. 이 API에서 처음 응답한다(그 전에는 되돌려주지 않는다) */
	public record PreJudgmentView(Long rangeOptionId, String label, List<Long> factorIds) { }
}
