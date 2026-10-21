package com.team2.project.profile.domain;

/**
 * 도전과제 판정에 쓰는 사용자 값 (BE-96). DB에서 그때그때 센다(저장하지 않는다).
 * collected가 null이면 수집 도전과제는 PENDING이다(BE-83 전)
 */
public record ProfileStats(int completed, boolean hasType, int attendanceDays, Integer collected) {

	public static final ProfileStats EMPTY = new ProfileStats(0, false, 0, null);

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
