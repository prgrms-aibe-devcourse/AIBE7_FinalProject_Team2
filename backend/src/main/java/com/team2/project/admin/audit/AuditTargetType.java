package com.team2.project.admin.audit;

/** 감사 로그의 대상 종류 (BE-68). DB CHECK(chk_admin_audit_log_target_type)와 같은 값이다 */
public enum AuditTargetType {
	CASE,
	JUDGMENT,
	FACTOR,
	CASE_SOURCE
}
