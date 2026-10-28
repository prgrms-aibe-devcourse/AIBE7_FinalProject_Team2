package com.team2.project.legalcase.dto;

import java.util.List;

import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.Difficulty;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.dto.CaseListResponse.MyStatus;

/**
 * API 16. 공개 사건 상세 (S-20 사건 디테일, BE-74). 체험 없이 부른다.
 * API 1 카드 값에 죄명(chargeName) · 사건 개요(overview)를 더한다.
 * 법정형 · 선고 가능 범위 · 권고 범위 · 판결 · 섹션 본문은 넣지 않는다 (FR-2-8, API 4와 같은 기준).
 *
 * @param scriptReady 승인된 공개 대본이 있는가 (BE-116). API 18이 200을 주는 조건과 같다 — false면 "준비 중"
 * @param myStatus 이 사건에 대한 내 최신 회차 상태(API 1과 같은 기준). 쿠키가 없거나 체험 전이면 null.
 *                 사용자마다 다르므로 HTTP 캐시를 켤 때 public 금지 — private + Vary: Cookie (API 1과 같음)
 */
public record CaseDetailResponse(
		Long caseId,
		String title,
		CrimeType crimeType,
		String crimeCategoryLabel,
		String chargeName,
		String shortIntro,
		List<String> keywords,
		Difficulty difficulty,
		Integer estimatedMinutes,
		long participantCount,
		String thumbnailUrl,
		String overview,
		boolean scriptReady,
		MyStatus myStatus) {

	public static CaseDetailResponse of(LegalCase legalCase, long participantCount, boolean scriptReady,
			MyStatus myStatus) {
		CrimeType crimeType = legalCase.getCrimeType();
		List<String> keywords = legalCase.getKeywords() == null ? List.of() : legalCase.getKeywords();
		return new CaseDetailResponse(
				legalCase.getId(),
				legalCase.getTitle(),
				crimeType,
				crimeType.getCategoryLabel(),
				legalCase.getChargeName(),
				legalCase.getShortIntro(),
				keywords,
				legalCase.getDifficulty(),
				legalCase.getEstimatedMinutes(),
				participantCount,
				legalCase.getThumbnailUrl(),
				legalCase.getOverview(),
				scriptReady,
				myStatus);
	}
}
