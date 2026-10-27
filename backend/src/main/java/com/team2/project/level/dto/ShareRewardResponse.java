package com.team2.project.level.dto;

/** SNS 공유 보상 결과 (L-3). awardedXp가 0이면 이미 받았거나 오늘 상한을 채운 것이다 */
public record ShareRewardResponse(int awardedXp, LevelStatus level) {
}
