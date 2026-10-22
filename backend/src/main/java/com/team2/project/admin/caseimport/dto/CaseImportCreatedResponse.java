package com.team2.project.admin.caseimport.dto;

import com.team2.project.admin.caseimport.domain.CaseImport;

/** 사건 등록 시작 응답 (API 명세 A-14, 202). 진행 상황은 A-15로 따로 묻는다 */
public record CaseImportCreatedResponse(Long importId, String status) {

	public static CaseImportCreatedResponse from(CaseImport entity) {
		return new CaseImportCreatedResponse(entity.getId(), entity.getStatus().name());
	}
}
