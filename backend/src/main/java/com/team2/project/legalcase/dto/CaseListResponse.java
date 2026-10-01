package com.team2.project.legalcase.dto;

import java.util.List;
import java.util.Map;

import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.Difficulty;
import com.team2.project.legalcase.domain.LegalCase;

/** API 1 응답. */
public record CaseListResponse(Summary summary, List<CaseItem> cases) {

	/** 필터와 관계없이 전체 PUBLISHED 사건 기준 건수 (칩 옆 건수 표시용). */
	public record Summary(int total, Map<CrimeType, Integer> byCrimeType) {
	}

	public record CaseItem(
			Long caseId,
			String title,
			CrimeType crimeType,
			String crimeCategoryLabel,
			String shortIntro,
			List<String> keywords,
			Difficulty difficulty,
			Integer estimatedMinutes,
			long participantCount,
			String thumbnailUrl) {

		public static CaseItem of(LegalCase legalCase, long participantCount) {
			CrimeType crimeType = legalCase.getCrimeType();
			List<String> keywords = legalCase.getKeywords() == null ? List.of() : legalCase.getKeywords();
			return new CaseItem(
					legalCase.getId(),
					legalCase.getTitle(),
					crimeType,
					crimeType.getCategoryLabel(),
					legalCase.getShortIntro(),
					keywords,
					legalCase.getDifficulty(),
					legalCase.getEstimatedMinutes(),
					participantCount,
					legalCase.getThumbnailUrl());
		}
	}
}
