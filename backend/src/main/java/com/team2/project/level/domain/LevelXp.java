package com.team2.project.level.domain;

import com.team2.project.profile.domain.AchievementJudge;
import com.team2.project.profile.domain.AchievementProgress;
import com.team2.project.profile.domain.ProfileStats;
import java.util.Map;

/**
 * 기존 기록에서 계산하는 경험치 (BE-106). 저장하지 않고 그때그때 센다 — 사건 완료 · 성향 테스트 · 출석 · 업적.
 * 모은 일러스트(건당 10)는 서버가 셀 수 없어(BE-83 전) 아직 0이다
 */
public final class LevelXp {

	static final int CASE_COMPLETED = 100;	// 사건 첫 완료 (사건당 1회)
	static final int PERSONALITY_TEST = 100;
	static final int ATTENDANCE_DAY = 5;

	/** 업적별 XP: 쉬운 것 50 · 중간 100 · 어려운 것 200 (ProfileDecoCatalog.ACHIEVEMENTS의 id) */
	private static final Map<String, Integer> ACHIEVEMENT_XP = Map.of(
		"first-case", 50, "personality", 50, "attend-3", 50,
		"attend-7", 100, "cases-3", 100, "collect-5", 100,
		"attend-30", 200, "collect-20", 200);

	private LevelXp() {
	}

	public static int derived(ProfileStats stats) {
		int xp = stats.completed() * CASE_COMPLETED
			+ (stats.hasType() ? PERSONALITY_TEST : 0)
			+ stats.attendanceDays() * ATTENDANCE_DAY;
		for (AchievementProgress item : AchievementJudge.progress(stats)) {
			if (item.cleared()) {
				xp += ACHIEVEMENT_XP.getOrDefault(item.id(), 0);
			}
		}
		return xp;
	}
}
