package com.team2.project.admin.audit;

/** 감사 로그에 남기는 관리자 행동 (BE-68). DB CHECK(chk_admin_audit_log_action)와 같은 값이다 */
public enum AdminAuditAction {
	LOGIN_SUCCESS,
	LOGIN_FAILURE,
	LOGOUT,
	SOURCE_VIEW,		// A-7 원본 판결문 열람
	JUDGMENT_APPROVE,	// A-9
	JUDGMENT_REJECT,	// A-10
	CASE_UPDATE,		// A-11
	VALUE_AXIS_CONFIRM,	// A-12
	CASE_PUBLISH,		// A-13
	CASE_PHOTO_UPLOAD,	// 사건 사진 업로드 (BE-91)
	CASE_PAPER_GENERATE,	// 신문 지면 생성 (BE-92)
	CASE_PAPER_UPDATE,	// 신문 지면 수정 (BE-92)
	CASE_IMPORT,		// 사건 등록 시작 (BE-93, A-14). 원문은 남기지 않고 사건번호 · 심급만 남긴다
	CASE_IMPORT_LOAD,	// 사건 등록 적재 (BE-93 2차). 만들어진 사건 번호만 남긴다
	CASE_PHOTO_DELETE	// 발단 · 결론 사진 삭제 (BE-78 2차 검토, BE-101, A-13-9)
}
