package com.team2.project.legalcase.domain;

/**
 * 대본 블록 재생성 작업 상태 (BE-129 2차, case_script_regeneration.status). 파이프라인 서비스 작업 상태(사건 등록 A-15와 같은 값)를
 * 그대로 옮긴다 — DB CHECK 제약과 같은 값이다.
 */
public enum ScriptRegenerationStatus {

	QUEUED,
	RUNNING,
	/** 파이프라인이 끝났다. 관리자가 결과를 반영하면 {@code appliedAt}이 채워진다 */
	SUCCEEDED,
	FAILED;

	public boolean isFinished() {
		return this == SUCCEEDED || this == FAILED;
	}
}
