package com.team2.project.profile.domain;

/**
 * 도전과제 진행도 (BE-96). value는 목표에서 멈추고, PENDING이면 null이다(cleared는 false)
 */
public record AchievementProgress(String id, AchievementStat stat, int goal, Integer value, boolean cleared,
	AchievementStatus status, Rewards rewards) {
}
