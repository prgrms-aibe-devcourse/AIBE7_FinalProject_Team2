package com.team2.project.profile.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** 도전과제가 세는 값 (BE-96). JSON 이름은 FE-68 statsOf의 키와 같다 */
public enum AchievementStat {

	COMPLETED("completed"),		// 완료한 회차가 하나라도 있는 사건 수
	HAS_TYPE("hasType"),		// 성향 테스트 현재 결과가 있으면 1
	ATTENDANCE_DAYS("attendanceDays"),	// 누적 출석 일수 (BE-97)
	COLLECTED("collected");		// 모은 일러스트 수 — BE-83 전까지 서버가 계산하지 못한다(PENDING)

	private final String key;

	AchievementStat(String key) {
		this.key = key;
	}

	@JsonValue
	public String key() {
		return key;
	}
}
