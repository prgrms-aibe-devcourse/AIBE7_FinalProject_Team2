package com.team2.project.experience.dto;

import java.time.OffsetDateTime;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;

/** API 3 전용 응답. API 2의 응답 계약은 ExperienceResponse로 유지한다. */
public record ExperienceStatusResponse(
		Long caseId,
		int attemptNo,
		ExperienceStatus status,
		int lastReviewedStep,
		OffsetDateTime startedAt,
		PreJudgment preJudgment) {

	public static ExperienceStatusResponse of(Experience experience, PreJudgment preJudgment) {
		ExperienceResponse state = ExperienceResponse.from(experience);
		return new ExperienceStatusResponse(state.caseId(), state.attemptNo(), state.status(),
				state.lastReviewedStep(), state.startedAt(), preJudgment);
	}

	/** 본인 사전 판단의 선택 구간만 반환한다. 판단 요소와 다른 주체의 판결은 포함하지 않는다. */
	public record PreJudgment(Long rangeOptionId, String label) { }
}
