package com.team2.project.experience.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.legalcase.domain.PenaltyType;
import java.util.List;

public record ReviewResponse(ExperienceStatus status, int lastReviewedStep, Integer openStep,
	List<Section> sections, List<Integer> lockedSteps, Law law, List<String> summary) {
	public record Section(int step, String stage, boolean confirmed, List<Item> items) { }

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Item(String sectionType, String title, String content, List<Object> data) { }

	public record Law(String appliedLaw, String statutoryPenaltyText, List<AllowedRange> allowedRanges,
		String allowedRangeNote, Recommended recommended, List<Object> terms) { }
	public record AllowedRange(PenaltyType penaltyType, Long allowedMin, Long allowedMax, String text) { }
	public record Recommended(Integer minMonths, Integer maxMonths, String basis) { }
}
