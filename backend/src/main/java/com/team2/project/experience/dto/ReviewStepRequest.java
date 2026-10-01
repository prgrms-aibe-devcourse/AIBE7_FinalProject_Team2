package com.team2.project.experience.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** API 7 요청. step은 2 ~ 4만 받는다 (그 밖은 400 VALIDATION_ERROR, 상태 검사보다 먼저) */
public record ReviewStepRequest(@NotNull @Min(2) @Max(4) Integer step) { }
