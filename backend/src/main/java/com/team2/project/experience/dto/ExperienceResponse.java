package com.team2.project.experience.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;

/** API 2 · 3 응답. */
public record ExperienceResponse(
		Long caseId,
		int attemptNo,
		ExperienceStatus status,
		int lastReviewedStep,
		OffsetDateTime startedAt) {

	/** API 명세서 1-1: 시각은 +09:00 오프셋으로 내려준다. */
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static ExperienceResponse from(Experience experience) {
		return new ExperienceResponse(
				experience.getCaseId(),
				experience.getAttemptNo(),
				experience.getStatus(),
				experience.getLastReviewedStep(),
				experience.getStartedAt().atZoneSameInstant(KST).toOffsetDateTime());
	}
}
