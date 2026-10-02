package com.team2.project.judgment.service;

import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.JudgmentFactor;
import com.team2.project.judgment.domain.SubjectType;
import com.team2.project.judgment.domain.UserSummarySentence;
import com.team2.project.judgment.dto.JudgmentView;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 판결 응답 공통 형식 조립 (API 10 · 12, 이후 API 14도 그대로 쓴다)
 * DB에 접근하지 않는다. 필요한 판단 요소 기록은 호출 쪽이 한 번에 읽어 넘긴다 (N+1 방지).
 */
@Component
public class JudgmentViewAssembler {

	/** 판단 1건을 응답 형식으로. factors는 이 판단의 요소 기록(표시 순서) */
	public JudgmentView toView(Judgment judgment, List<JudgmentFactor> factors) {
		List<JudgmentFactor> considered = factors == null ? List.of() : factors;
		boolean user = judgment.getSubjectType() == SubjectType.USER;
		boolean court = judgment.getSubjectType() == SubjectType.COURT;
		return new JudgmentView(
			judgment.getSubjectType(),
			judgment.getPenaltyType(),
			judgment.getReducedTo(),
			judgment.getPrisonMonths(),
			judgment.getFineAmount(),
			judgment.getSuspensionMonths(),
			judgment.getExtraDispositions() == null ? List.of() : judgment.getExtraDispositions(),
			// USER는 저장값이 없고 요약 태그로 규칙 문장을 만든다 (API 명세 판결 응답 공통 형식)
			user ? UserSummarySentence.of(considered) : judgment.getSummary(),
			user ? null : judgment.getReasoning(),
			court ? judgment.getExcerpt() : null,
			court ? judgment.getPlainExplanation() : null,
			considered.stream()
				.map(factor -> new JudgmentView.FactorView(
					factor.getFactor().getId(),
					factor.getFactor().getLabel(),
					factor.getDirection(),
					court ? factor.getEvidence() : null))
				.toList());
	}
}
