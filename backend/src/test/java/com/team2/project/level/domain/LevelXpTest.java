package com.team2.project.level.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.team2.project.profile.domain.ProfileStats;
import com.team2.project.profile.domain.UnlockMark;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 기존 기록에서 계산하는 경험치 (BE-106) */
class LevelXpTest {

	@Test
	@DisplayName("기록이 없으면 0")
	void empty() {
		assertThat(LevelXp.derived(ProfileStats.EMPTY)).isZero();
	}

	@Test
	@DisplayName("첫 사건 완료: 사건 100 + 업적 first-case 50")
	void firstCase() {
		assertThat(LevelXp.derived(new ProfileStats(1, false, 0, null))).isEqualTo(150);
	}

	@Test
	@DisplayName("성향 테스트: 100 + 업적 personality 50. 출석은 하루 5 + 3일 업적 50")
	void personalityAndAttendance() {
		assertThat(LevelXp.derived(new ProfileStats(0, true, 0, null))).isEqualTo(150);
		assertThat(LevelXp.derived(new ProfileStats(0, false, 2, null))).isEqualTo(10);
		assertThat(LevelXp.derived(new ProfileStats(0, false, 3, null))).isEqualTo(15 + 50);
	}

	@Test
	@DisplayName("서버가 셀 수 없는 일러스트 수집(null)은 0 — 수집 업적도 주지 않는다")
	void collectedPending() {
		assertThat(LevelXp.derived(new ProfileStats(0, false, 0, null))).isZero();
	}

	@Test
	@DisplayName("모은 일러스트 · 의상은 1장에 10 (BE-83). 5장이면 50 + 업적 collect-5 100")
	void collectedItems() {
		assertThat(LevelXp.derived(new ProfileStats(0, false, 0, 4))).isEqualTo(40);
		assertThat(LevelXp.derived(new ProfileStats(0, false, 0, 5))).isEqualTo(50 + 100);
	}

	@Test
	@DisplayName("해금 기록이 있는 업적은 계산값이 미달이어도 XP가 그대로다 (BE-83)")
	void unlockedAchievementKeepsXp() {
		ProfileStats stats = new ProfileStats(0, false, 0, 0,
			Map.of("cases-3", new UnlockMark(Instant.parse("2026-10-29T00:00:00Z"), true)));
		assertThat(LevelXp.derived(stats)).isEqualTo(100);
	}
}
