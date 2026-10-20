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
	CASE_PHOTO_UPLOAD	// 사건 사진 업로드 (BE-91)
}
