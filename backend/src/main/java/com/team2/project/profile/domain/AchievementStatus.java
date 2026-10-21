package com.team2.project.profile.domain;

/** 도전과제 상태 (BE-96). PENDING은 서버가 아직 판정할 수 없는 것(수집, BE-83 전) — 잠김으로 다룬다 */
public enum AchievementStatus {
	CLEARED,
	IN_PROGRESS,
	PENDING
}
