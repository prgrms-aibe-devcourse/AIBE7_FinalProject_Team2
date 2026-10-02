package com.team2.project.judgment.dto;

import com.team2.project.experience.domain.ExperienceStatus;

/**
 * 공개 요청 응답 (API 11 실제 판결 공개, API 13 비교 공개)
 * 다시 보내도 거절하지 않고, 그때의 현재 상태를 돌려준다 (API 명세 2장).
 */
public record RevealResponse(ExperienceStatus status) { }
