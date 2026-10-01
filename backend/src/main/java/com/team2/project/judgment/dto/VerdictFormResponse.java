package com.team2.project.judgment.dto;

import com.team2.project.legalcase.domain.PenaltyType;
import java.util.List;

public record VerdictFormResponse(
	List<PenaltyOption> penaltyOptions,
	String statutoryPenaltyText,
	String allowedRangeNote,
	Recommended recommended,
	Suspension suspensionRule,
	List<FactorItem> factors
) {
	public record PenaltyOption(PenaltyType penaltyType, Long allowedMin, Long allowedMax,
		String text, boolean suspensionAllowed, List<PenaltyType> reducibleTo) { }
	public record Recommended(Integer minMonths, Integer maxMonths, String basis) { }
	public record Suspension(int maxPrisonMonths, long maxFineAmount, int minMonths, int maxMonths) { }
	public record FactorItem(Long factorId, String label) { }
}
