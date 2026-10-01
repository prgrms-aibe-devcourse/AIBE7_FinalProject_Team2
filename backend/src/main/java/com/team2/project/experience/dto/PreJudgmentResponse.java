package com.team2.project.experience.dto;

import com.team2.project.experience.domain.ExperienceStatus;

/** API 5 응답. 사전 판단 내용은 S-09 전까지 다시 보여 주지 않으므로 되돌려주지 않는다. */
public record PreJudgmentResponse(ExperienceStatus status, int lastReviewedStep) {
}
