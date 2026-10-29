package com.team2.project.profile.domain;

import java.time.ZoneId;
import java.util.List;

/** 도전과제 판정 · 열린 항목 계산 (BE-96). 순수 함수라 DB 없이 테스트한다 */
public final class AchievementJudge {

	/** 해금 시각을 내려 줄 때의 시간대 (마이페이지 다른 시각과 같은 한국 시간) */
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private AchievementJudge() {
	}

	/**
	 * 도전과제마다 진행도.
	 * - 해금 기록이 있으면 계산값과 상관없이 CLEARED · value = goal (열렸던 도전과제는 다시 잠기지 않는다, BE-96 원칙 · BE-83)
	 * - 서버가 셀 수 없는 값(collected == null)은 PENDING · value null · 잠김
	 * - 나머지는 계산값으로 판정한다. 막 달성했지만 아직 기록 전이면 unlockedAt은 null이다(마이페이지가 기록한 뒤 다시 판정한다)
	 */
	public static List<AchievementProgress> progress(ProfileStats stats) {
		return ProfileDecoCatalog.ACHIEVEMENTS.stream().map(item -> {
			UnlockMark mark = stats.unlocks().get(item.id());
			if (mark != null) {
				return new AchievementProgress(item.id(), item.stat(), item.goal(), item.goal(), true,
					AchievementStatus.CLEARED, item.rewards(), mark.unlockedAt().atZone(KST).toOffsetDateTime(),
					!mark.seen());
			}
			Integer raw = stats.valueOf(item.stat());
			if (raw == null) {
				return new AchievementProgress(item.id(), item.stat(), item.goal(), null, false, AchievementStatus.PENDING,
					item.rewards(), null, false);
			}
			boolean cleared = raw >= item.goal();
			return new AchievementProgress(item.id(), item.stat(), item.goal(), Math.min(raw, item.goal()), cleared,
				cleared ? AchievementStatus.CLEARED : AchievementStatus.IN_PROGRESS, item.rewards(), null, false);
		}).toList();
	}

	/** 지금 CLEARED인데 해금 기록이 없는 도전과제 id (새로 남겨야 할 해금, BE-83). 없으면 빈 목록 */
	public static List<String> unrecordedClears(ProfileStats stats) {
		return progress(stats).stream()
			.filter(item -> item.cleared() && !stats.unlocks().containsKey(item.id()))
			.map(AchievementProgress::id)
			.toList();
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
