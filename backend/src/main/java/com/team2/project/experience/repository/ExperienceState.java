package com.team2.project.experience.repository;

import com.team2.project.experience.domain.ExperienceStatus;

/**
 * 체험의 현재 상태 + 마지막 확인 섹션만 (조건부 갱신이 0건일 때 재확인용).
 * 상태만으로는 섹션 확인(API 7)의 "이미 그 섹션 이상" 판정을 할 수 없어 status만으로는 부족하다.
 */
public record ExperienceState(ExperienceStatus status, int lastReviewedStep) {
}
