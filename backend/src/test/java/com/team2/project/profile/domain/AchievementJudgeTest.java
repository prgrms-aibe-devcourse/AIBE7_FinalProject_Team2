package com.team2.project.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 도전과제 판정 · 열린 항목 (BE-96) */
class AchievementJudgeTest {

	private static Map<String, AchievementProgress> byId(List<AchievementProgress> progress) {
		return progress.stream().collect(Collectors.toMap(AchievementProgress::id, Function.identity()));
	}

	@Test
	@DisplayName("기록이 없으면 진행 중 · 수집은 판정 대기(PENDING, value null, 잠김)이고 열린 것은 FREE뿐")
	void emptyStats() {
		List<AchievementProgress> progress = AchievementJudge.progress(ProfileStats.EMPTY);

		assertThat(progress).hasSize(ProfileDecoCatalog.ACHIEVEMENTS.size());
		Map<String, AchievementProgress> items = byId(progress);
		assertThat(items.get("first-case").status()).isEqualTo(AchievementStatus.IN_PROGRESS);
		assertThat(items.get("first-case").value()).isZero();
		assertThat(items.get("collect-5").status()).isEqualTo(AchievementStatus.PENDING);
		assertThat(items.get("collect-5").value()).isNull();
		assertThat(items.get("collect-5").cleared()).isFalse();
		assertThat(AchievementJudge.unlocked(progress)).isEqualTo(ProfileDecoCatalog.FREE);
	}

	@Test
	@DisplayName("목표를 넘으면 value는 목표에서 멈추고, 달성한 보상이 열린다")
	void clearedRewardsUnlock() {
		List<AchievementProgress> progress = AchievementJudge.progress(new ProfileStats(5, true, 2, null));
		Map<String, AchievementProgress> items = byId(progress);

		assertThat(items.get("first-case").value()).isEqualTo(1);
		assertThat(items.get("cases-3").value()).isEqualTo(3);
		assertThat(items.get("cases-3").status()).isEqualTo(AchievementStatus.CLEARED);
		assertThat(items.get("personality").cleared()).isTrue();
		assertThat(items.get("attend-3").value()).isEqualTo(2);
		assertThat(items.get("attend-3").status()).isEqualTo(AchievementStatus.IN_PROGRESS);

		Rewards open = AchievementJudge.unlocked(progress);
		assertThat(open.frames()).containsExactly("basic", "wood", "stitch");
		assertThat(open.sashColors()).contains("short:wine", "long:wine", "short:navy", "long:navy");
		assertThat(open.backgrounds()).contains("tarot", "linen").doesNotContain("news", "latte");
		assertThat(open.kinds()).contains("characters/scene", "characters/personality").doesNotContain("cases");
	}

	@Test
	@DisplayName("수집 수가 오면(BE-83 이후) 수집 도전과제도 판정한다")
	void collectedJudgedWhenKnown() {
		Map<String, AchievementProgress> items = byId(AchievementJudge.progress(new ProfileStats(0, false, 0, 20)));

		assertThat(items.get("collect-5").status()).isEqualTo(AchievementStatus.CLEARED);
		assertThat(items.get("collect-20").status()).isEqualTo(AchievementStatus.CLEARED);
	}
}
