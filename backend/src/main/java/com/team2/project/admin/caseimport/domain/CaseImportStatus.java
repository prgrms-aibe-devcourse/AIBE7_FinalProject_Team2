package com.team2.project.admin.caseimport.domain;

/**
 * 사건 등록 작업 상태 (BE-93, API 명세 A-15). case_import.status 컬럼. DB CHECK 제약과 같은 값을 쓴다.
 *
 * <p>파이프라인 서비스(COMMON-50)가 돌려주는 값과 같은 이름이다 — 옮기면서 뜻이 달라지지 않게 했다.
 */
public enum CaseImportStatus {

	/** 작업을 만들었고 아직 시작 전 */
	QUEUED,
	/** 파이프라인이 도는 중 */
	RUNNING,
	/** 파이프라인이 끝났다. 적재까지 끝나면 caseId가 채워진다 (적재는 2차 PR) */
	SUCCEEDED,
	/** 중간에 실패했다. errorCode · message로 이유를 내려 준다 */
	FAILED;

	public boolean isFinished() {
		return this == SUCCEEDED || this == FAILED;
	}
}
