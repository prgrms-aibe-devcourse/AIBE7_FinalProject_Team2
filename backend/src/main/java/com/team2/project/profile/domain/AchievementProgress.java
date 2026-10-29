package com.team2.project.profile.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * 도전과제 진행도 (BE-96). value는 목표에서 멈추고, PENDING이면 null이다(cleared는 false).
 * unlockedAt · isNew(BE-83): 해금 기록이 있을 때만 값이 있다. isNew는 해금됐고 아직 보지 않았으면 true("새로 열림")
 */
public record AchievementProgress(String id, AchievementStat stat, int goal, Integer value, boolean cleared,
	AchievementStatus status, Rewards rewards, OffsetDateTime unlockedAt, @JsonProperty("isNew") boolean isNew) {
}
