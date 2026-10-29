package com.team2.project.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
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
	@DisplayName("기록이 없으면 모두 진행 중(수집도 0부터, BE-83)이고 열린 것은 FREE뿐 · 해금 시각 없음")
	void emptyStats() {
		List<AchievementProgress> progress = AchievementJudge.progress(ProfileStats.EMPTY);

		assertThat(progress).hasSize(ProfileDecoCatalog.ACHIEVEMENTS.size());
		Map<String, AchievementProgress> items = byId(progress);
		assertThat(items.get("first-case").status()).isEqualTo(AchievementStatus.IN_PROGRESS);
		assertThat(items.get("first-case").value()).isZero();
		assertThat(items.get("collect-5").status()).isEqualTo(AchievementStatus.IN_PROGRESS);
		assertThat(items.get("collect-5").value()).isZero();
		assertThat(progress).allSatisfy(item -> {
			assertThat(item.unlockedAt()).isNull();
			assertThat(item.isNew()).isFalse();
		});
		assertThat(AchievementJudge.unlocked(progress)).isEqualTo(ProfileDecoCatalog.FREE);
	}

	@Test
	@DisplayName("새로 남길 해금은 CLEARED인데 기록이 없는 것만 (BE-83). 기록이 다 있으면 빈 목록 → 마이페이지가 별도 트랜잭션을 열지 않는다")
	void unrecordedClears() {
		ProfileStats stats = new ProfileStats(3, true, 0, 0,
			Map.of("first-case", new UnlockMark(Instant.parse("2026-10-29T00:00:00Z"), true)));

		assertThat(AchievementJudge.unrecordedClears(stats)).containsExactly("personality", "cases-3");
		assertThat(AchievementJudge.unrecordedClears(ProfileStats.EMPTY)).isEmpty();
	}

	@Test
	@DisplayName("서버가 셀 수 없는 값(collected null)은 판정 대기(PENDING, value null, 잠김)")
	void unknownStatPending() {
		Map<String, AchievementProgress> items = byId(AchievementJudge.progress(new ProfileStats(0, false, 0, null)));

		assertThat(items.get("collect-5").status()).isEqualTo(AchievementStatus.PENDING);
		assertThat(items.get("collect-5").value()).isNull();
		assertThat(items.get("collect-5").cleared()).isFalse();
	}

	@Test
	@DisplayName("해금 기록이 있으면 계산값이 미달이어도 CLEARED · value = goal · 보상 유지, 안 봤으면 isNew (BE-83)")
	void unlockRecordKeepsCleared() {
		Instant unlockedAt = Instant.parse("2026-10-29T01:40:00Z");
		ProfileStats stats = new ProfileStats(0, false, 0, 0, Map.of(
			"cases-3", new UnlockMark(unlockedAt, false),	// 표가 바뀌어 지금은 미달 · 아직 안 봄
			"first-case", new UnlockMark(unlockedAt, true)));	// 이미 봄
		List<AchievementProgress> progress = AchievementJudge.progress(stats);
		Map<String, AchievementProgress> items = byId(progress);

		AchievementProgress cases3 = items.get("cases-3");
		assertThat(cases3.status()).isEqualTo(AchievementStatus.CLEARED);
		assertThat(cases3.cleared()).isTrue();
		assertThat(cases3.value()).isEqualTo(cases3.goal());
		assertThat(cases3.unlockedAt()).isEqualTo(OffsetDateTime.parse("2026-10-29T10:40:00+09:00"));	// 한국 시간
		assertThat(cases3.isNew()).isTrue();
		assertThat(items.get("first-case").isNew()).isFalse();
		assertThat(AchievementJudge.unlocked(progress).frames()).contains("stitch", "wood");
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
