package com.team2.project.profile.domain;

/**
 * 도전과제 상태 (BE-96). PENDING은 서버가 아직 판정할 수 없는 것 — 잠김으로 다룬다.
 * 수집(collected)은 BE-83부터 서버가 세므로 지금은 PENDING이 나오지 않지만, 이후 서버가 셀 수 없는 값이 생길 때를 위해 남긴다
 */
public enum AchievementStatus {
	CLEARED,
	IN_PROGRESS,
	PENDING
}
