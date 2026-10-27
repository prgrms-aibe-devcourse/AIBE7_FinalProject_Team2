package com.team2.project.level.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.team2.project.profile.domain.ProfileStats;
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
		assertThat(LevelXp.derived(new ProfileStats(0, false, 0, 5))).isEqualTo(100);
	}
}
