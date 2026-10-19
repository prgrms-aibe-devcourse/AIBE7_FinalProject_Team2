package com.team2.project.personality.dto;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.ValueAxis;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * P-5 마이페이지 (API 명세 9장). 기록이 없으면 null · 빈 배열인 빈 마이페이지
 */
public record MyPageResponse(
	PersonalityResultResponse personality,
	JudgmentPersonality judgmentPersonality,
	List<HistoryItem> history,
	List<ExperienceItem> experiences) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static MyPageResponse empty() {
		return new MyPageResponse(null, null, List.of(), List.of());
	}

	/** 종합 판결 성향. type은 네 축 모두 값이 있을 때만 있다 (성향 산식 4장) */
	public record JudgmentPersonality(Map<ValueAxis, Double> axisScores, PersonalityTypeResponse type,
		int experienceCount) {
	}

	/** 성향 변화 흐름의 한 점: 사건의 첫 회차 체험별 축 점수 (자료 없는 축은 null) */
	public record HistoryItem(Long caseId, String title, OffsetDateTime completedAt, Map<ValueAxis, Double> axisScores) {

		public static HistoryItem of(Experience experience, Instant fallbackCompletedAt, Map<ValueAxis, Double> axisScores) {
			Instant completedAt = experience.getCompletedAt() != null ? experience.getCompletedAt() : fallbackCompletedAt;
			return new HistoryItem(experience.getLegalCase().getId(), experience.getLegalCase().getTitle(),
				kst(completedAt), axisScores);
		}
	}

	/** 체험한 사건 (사건마다 최근 회차) */
	public record ExperienceItem(Long caseId, String title, CrimeType crimeType, ExperienceStatus status, int attemptNo,
		OffsetDateTime updatedAt) {

		public static ExperienceItem from(Experience experience) {
			return new ExperienceItem(experience.getLegalCase().getId(), experience.getLegalCase().getTitle(),
				experience.getLegalCase().getCrimeType(), experience.getStatus(), experience.getAttemptNo(),
				kst(experience.getUpdatedAt()));
		}
	}

	private static OffsetDateTime kst(Instant instant) {
		return instant == null ? null : instant.atZone(KST).toOffsetDateTime();
	}
}
