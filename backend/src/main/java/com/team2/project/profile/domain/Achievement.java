package com.team2.project.profile.domain;

/** 도전과제 한 줄 (BE-96). 제목 · 문구 · 그림은 프론트가 id로 붙인다 */
public record Achievement(String id, AchievementStat stat, int goal, Rewards rewards) {
}
