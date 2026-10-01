package com.team2.project.experience.domain;

/**
 * 체험 진행 상태 (IA 9장, experience.status)
 * 선언 순서가 곧 진행 순서이며, 상태는 앞으로만 이동한다.
 */
public enum ExperienceStatus {
	STARTED,			// 체험 시작 (S-03)
	PRE_JUDGED,			// 사전 판단 제출 완료
	REVIEWING,			// 사건 정보 확인 중 (S-04)
	REVIEWED,			// 사건 정보 확인 완료 (S-05 · S-06 가능)
	VERDICT_CONFIRMED,	// 판결 확정 → AI 판결 조회 가능
	AI_REVEALED,		// 실제 판결 공개 → 실제 판결 조회 가능
	COMPLETED;			// 비교 공개 → 세 판결 비교 조회 가능

	/** 현재 상태가 required 이상인지 (결과 조회 가능 여부 판단용) */
	public boolean isAtLeast(ExperienceStatus required) {
		return this.ordinal() >= required.ordinal();
	}

	/** next가 이 상태 바로 다음 상태인지 */
	public boolean isNext(ExperienceStatus next) {
		return next.ordinal() == this.ordinal() + 1;
	}
}
