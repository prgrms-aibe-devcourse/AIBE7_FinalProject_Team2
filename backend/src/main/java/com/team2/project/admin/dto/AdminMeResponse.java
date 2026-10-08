package com.team2.project.admin.dto;

import com.team2.project.admin.service.AdminPrincipal;

/** 로그인한 관리자 정보 */
public record AdminMeResponse(Long adminId, String email, String displayName) {

	public static AdminMeResponse from(AdminPrincipal principal) {
		return new AdminMeResponse(principal.adminId(), principal.email(), principal.displayName());
	}
}
