package com.team2.project.experience.dto;

import com.team2.project.experience.domain.ExperienceStatus;

public record ReviewStepResponse(ExperienceStatus status, int lastReviewedStep, Integer openStep) { }
