package com.team2.project.level.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.level.dto.LevelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 레벨 곡선 · 직업 해금 (BE-106). 순수 계산이라 DB 없이 돈다 */
class LevelCurveTest {

	@Test
	@DisplayName("직업 해금 레벨의 누적 XP는 기획 표(k=0.08)와 같다")
	void xpAtUnlockLevels() {
		int[] levels = {1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
		int[] expected = {0, 385, 854, 1377, 1967, 2645, 3442, 4408, 5634, 7314, 10000};
		for (int i = 0; i < levels.length; i++) {
			assertThat(LevelCurve.xpAt(levels[i])).as("Lv%d", levels[i]).isEqualTo(expected[i]);
		}
	}

	@Test
	@DisplayName("레벨당 필요 XP는 갈수록 커진다 — 초반이 빠르고 후반이 느리다 (반올림 ±1 허용)")
	void stepsGrow() {
		for (int level = 2; level < LevelCurve.MAX_LEVEL; level++) {
			int previous = LevelCurve.xpAt(level) - LevelCurve.xpAt(level - 1);
			int next = LevelCurve.xpAt(level + 1) - LevelCurve.xpAt(level);
			assertThat(next).as("Lv%d→%d", level, level + 1).isGreaterThanOrEqualTo(previous - 1);
		}
		assertThat(LevelCurve.xpAt(2) - LevelCurve.xpAt(1)).isEqualTo(41);
		assertThat(LevelCurve.xpAt(100) - LevelCurve.xpAt(99)).isEqualTo(352);
	}

	@Test
	@DisplayName("levelOf: 경계 XP에서 그 레벨, 한 칸 모자라면 이전 레벨. 음수 · 초과는 양끝")
	void levelOfBoundaries() {
		assertThat(LevelCurve.levelOf(0)).isEqualTo(1);
		assertThat(LevelCurve.levelOf(-5)).isEqualTo(1);
		assertThat(LevelCurve.levelOf(384)).isLessThan(10);
		assertThat(LevelCurve.levelOf(385)).isEqualTo(10);
		assertThat(LevelCurve.levelOf(9999)).isEqualTo(99);
		assertThat(LevelCurve.levelOf(10000)).isEqualTo(100);
		assertThat(LevelCurve.levelOf(50000)).isEqualTo(100);
	}

	@Test
	@DisplayName("xpAt은 범위 밖 레벨을 거절한다")
	void xpAtOutOfRange() {
		assertThatThrownBy(() -> LevelCurve.xpAt(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> LevelCurve.xpAt(101)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("직업 11등급: 10레벨마다 하나씩 열리고, 레벨에서 열린 가장 높은 직업을 돌려준다")
	void jobs() {
		assertThat(Job.values()).hasSize(11);
		assertThat(Job.highestAt(1)).isEqualTo(Job.NONE);
		assertThat(Job.highestAt(9)).isEqualTo(Job.NONE);
		assertThat(Job.highestAt(10)).isEqualTo(Job.LAW_STUDENT);
		assertThat(Job.highestAt(99)).isEqualTo(Job.JUSTICE);
		assertThat(Job.highestAt(100)).isEqualTo(Job.CHIEF_JUSTICE);
		assertThat(Job.fromCode("JUDGE")).contains(Job.JUDGE);
		assertThat(Job.fromCode("nope")).isEmpty();
	}

	@Test
	@DisplayName("LevelStatus: 기록이 없으면 Lv1 · 무직, 고른 직업이 잠겼으면 무시하고 열린 가장 높은 직업을 보여 준다")
	void status() {
		LevelStatus initial = LevelStatus.initial();
		assertThat(initial.level()).isEqualTo(1);
		assertThat(initial.job().code()).isEqualTo("NONE");
		assertThat(initial.nextLevelXp()).isEqualTo(LevelCurve.xpAt(2));

		LevelStatus locked = LevelStatus.of(400, Job.JUDGE);	// Lv10 — 판사(Lv40)는 아직 잠김
		assertThat(locked.level()).isEqualTo(10);
		assertThat(locked.job().code()).isEqualTo("LAW_STUDENT");
		assertThat(locked.jobs()).filteredOn(item -> item.unlocked()).hasSize(2);

		LevelStatus chosen = LevelStatus.of(400, Job.NONE);
		assertThat(chosen.job().code()).isEqualTo("NONE");
		assertThat(chosen.jobs()).filteredOn(item -> item.selected()).extracting(item -> item.code()).containsExactly("NONE");

		LevelStatus max = LevelStatus.of(10000, null);
		assertThat(max.level()).isEqualTo(100);
		assertThat(max.nextLevelXp()).isNull();
		assertThat(max.job().name()).isEqualTo("대법원장");
	}
}
