package com.team2.project.experience.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.SentenceRangeOption;

/**
 * API 4 응답. 법정형 · 선고 가능 범위 · 권고 범위 · 실제 판결은 넣지 않는다 (FR-2-8).
 * 필드를 늘릴 때는 이 원칙에 어긋나지 않는지 먼저 확인한다.
 */
public record OverviewResponse(
		@JsonProperty("case") CaseOverview caseOverview,
		List<RangeOption> rangeOptions,
		List<PreFactor> preFactors) {

	public record CaseOverview(
			Long caseId,
			String title,
			CrimeType crimeType,
			String crimeCategoryLabel,
			String chargeName,
			String overview) {
	}

	public record RangeOption(Long rangeOptionId, String label) {
	}

	public record PreFactor(Long factorId, String label) {
	}

	public static OverviewResponse of(LegalCase legalCase, List<SentenceRangeOption> rangeOptions,
			List<Factor> overviewFactors) {
		CrimeType crimeType = legalCase.getCrimeType();
		return new OverviewResponse(
				new CaseOverview(legalCase.getId(), legalCase.getTitle(), crimeType, crimeType.getCategoryLabel(),
						legalCase.getChargeName(), legalCase.getOverview()),
				rangeOptions.stream().map(option -> new RangeOption(option.getId(), option.getLabel())).toList(),
				overviewFactors.stream()
						.map(factor -> new PreFactor(factor.getId(),
								factor.getPreLabel() != null ? factor.getPreLabel() : factor.getLabel()))
						.toList());
	}
}
