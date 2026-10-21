package com.team2.project.profile.domain;

import java.util.List;

/** 도전과제 판정 · 열린 항목 계산 (BE-96). 순수 함수라 DB 없이 테스트한다 */
public final class AchievementJudge {

	private AchievementJudge() {
	}

	/** 도전과제마다 진행도. 서버가 셀 수 없는 값(collected)은 PENDING · value null · 잠김 */
	public static List<AchievementProgress> progress(ProfileStats stats) {
		return ProfileDecoCatalog.ACHIEVEMENTS.stream().map(item -> {
			Integer raw = stats.valueOf(item.stat());
			if (raw == null) {
				return new AchievementProgress(item.id(), item.stat(), item.goal(), null, false, AchievementStatus.PENDING,
					item.rewards());
			}
			boolean cleared = raw >= item.goal();
			return new AchievementProgress(item.id(), item.stat(), item.goal(), Math.min(raw, item.goal()), cleared,
				cleared ? AchievementStatus.CLEARED : AchievementStatus.IN_PROGRESS, item.rewards());
		}).toList();
	}

	/** 지금 열린 것: 처음부터 열린 것(FREE) + 달성한 도전과제의 보상 */
	public static Rewards unlocked(List<AchievementProgress> progress) {
		Rewards open = ProfileDecoCatalog.FREE;
		for (AchievementProgress item : progress) {
			if (item.cleared()) {
				open = open.plus(item.rewards());
			}
		}
		return open;
	}
}
