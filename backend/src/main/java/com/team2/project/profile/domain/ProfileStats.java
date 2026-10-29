package com.team2.project.profile.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * 도전과제 판정에 쓰는 사용자 값 (BE-96). DB에서 그때그때 센다(진행도는 저장하지 않는다).
 * collected: 모은 일러스트 + 의상 수(BE-83). null이면 서버가 셀 수 없다는 뜻이고 수집 도전과제는 PENDING이다.
 * unlocks: 도전과제 해금 기록(BE-83). 기록이 있으면 계산값이 미달이어도 CLEARED를 유지한다
 */
public record ProfileStats(int completed, boolean hasType, int attendanceDays, Integer collected,
	Map<String, UnlockMark> unlocks) {

	public static final ProfileStats EMPTY = new ProfileStats(0, false, 0, 0, Map.of());

	public ProfileStats {
		unlocks = unlocks == null ? Map.of() : Map.copyOf(unlocks);
	}

	/** 해금 기록 없이 만든다 (판정 · 경험치 단위 테스트용) */
	public ProfileStats(int completed, boolean hasType, int attendanceDays, Integer collected) {
		this(completed, hasType, attendanceDays, collected, Map.of());
	}

	/** 해금 기록을 더한 값 (새로 기록한 해금을 같은 요청의 응답에 반영할 때) */
	public ProfileStats withUnlocks(Map<String, UnlockMark> added) {
		Map<String, UnlockMark> merged = new HashMap<>(unlocks);
		added.forEach(merged::putIfAbsent);	// 이미 있던 기록(처음 해금 시각)을 덮어쓰지 않는다
		return new ProfileStats(completed, hasType, attendanceDays, collected, merged);
	}

	/** 값이 없으면(서버가 셀 수 없으면) null */
	Integer valueOf(AchievementStat stat) {
		return switch (stat) {
			case COMPLETED -> completed;
			case HAS_TYPE -> hasType ? 1 : 0;
			case ATTENDANCE_DAYS -> attendanceDays;
			case COLLECTED -> collected;
		};
	}
}
