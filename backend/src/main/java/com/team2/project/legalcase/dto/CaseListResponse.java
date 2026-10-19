package com.team2.project.legalcase.dto;

import java.util.List;
import java.util.Map;

import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.Difficulty;
import com.team2.project.legalcase.domain.LegalCase;

/** API 1 응답. */
public record CaseListResponse(Summary summary, List<CaseItem> cases) {

	/** 필터와 관계없이 전체 PUBLISHED 사건 기준 건수 (칩 옆 건수 표시용). */
	public record Summary(int total, Map<CrimeType, Integer> byCrimeType) {
	}

	/**
	 * 목록 진행 상태 배지 (FE-19, REQ-010 · FR-1-6). 체험 전이면 CaseItem.myStatus가 null이다.
	 * 세부 상태(7단계)는 목록에 내보내지 않고 진행 중 / 완료 두 가지로만 묶는다.
	 */
	public enum MyStatus {
		IN_PROGRESS,	// 체험 시작 ~ 실제 판결 공개(AI_REVEALED)까지
		COMPLETED;		// 세 판결 비교까지 끝남

		public static MyStatus from(ExperienceStatus status) {
			return status == ExperienceStatus.COMPLETED ? COMPLETED : IN_PROGRESS;
		}
	}

	/**
	 * @param myStatus 이 사건에 대한 내 최신 회차 상태. 익명 ID 쿠키가 없거나 체험 전이면 null.
	 *                 사용자마다 다른 값이라 사건 목록을 캐시할 때(6.5.3)는 이 필드를 빼고 요청마다 붙인다 (기술 스택 2장).
	 *                 HTTP 캐시를 켤 때도 public 금지 — private + Vary: Cookie (API 명세 API 1, CaseListApiTest가 no-store를 고정)
	 */
	public record CaseItem(
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
			MyStatus myStatus) {

		public static CaseItem of(LegalCase legalCase, long participantCount, MyStatus myStatus) {
			CrimeType crimeType = legalCase.getCrimeType();
			List<String> keywords = legalCase.getKeywords() == null ? List.of() : legalCase.getKeywords();
			return new CaseItem(
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
					myStatus);
		}
	}
}
