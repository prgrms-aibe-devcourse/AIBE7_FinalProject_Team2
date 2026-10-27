package com.team2.project.level.dto;

import com.team2.project.level.domain.Job;
import com.team2.project.level.domain.LevelCurve;
import java.util.Arrays;
import java.util.List;

/**
 * 레벨 · 직업 조회 (BE-106, API 명세 L-1). 쿠키가 없거나 기록이 없으면 Lv1 · 0 XP · 무직
 *
 * @param currentLevelXp 지금 레벨에 닿은 누적 XP
 * @param nextLevelXp    다음 레벨에 필요한 누적 XP. 최고 레벨이면 null
 * @param job            카드에 보여 줄 직업 (고른 직업, 없으면 열린 가장 높은 직업)
 */
public record LevelStatus(int level, int xp, int currentLevelXp, Integer nextLevelXp, JobView job, List<JobItem> jobs) {

	public record JobView(String code, String name) {

		static JobView of(Job job) {
			return new JobView(job.name(), job.label());
		}
	}

	public record JobItem(String code, String name, int unlockLevel, int unlockXp, boolean unlocked, boolean selected) {
	}

	public static LevelStatus initial() {
		return of(0, null);
	}

	/** @param selected 사용자가 고른 직업(없으면 null). 아직 잠긴 직업이면 무시하고 열린 가장 높은 직업을 보여 준다 */
	public static LevelStatus of(int xp, Job selected) {
		int level = LevelCurve.levelOf(xp);
		Job shown = selected != null && selected.unlockedAt(level) ? selected : Job.highestAt(level);
		List<JobItem> jobs = Arrays.stream(Job.values())
			.map(item -> new JobItem(item.name(), item.label(), item.unlockLevel(), LevelCurve.xpAt(item.unlockLevel()),
				item.unlockedAt(level), item == shown))
			.toList();
		Integer next = level < LevelCurve.MAX_LEVEL ? LevelCurve.xpAt(level + 1) : null;
		return new LevelStatus(level, xp, LevelCurve.xpAt(level), next, JobView.of(shown), jobs);
	}
}
