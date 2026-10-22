package com.team2.project.admin.caseimport.dto;

import com.team2.project.admin.caseimport.domain.CaseImport;
import java.time.Instant;
import java.util.List;

/**
 * 사건 등록 진행 상황 (API 명세 A-15). 화면이 끝날 때까지 2초마다 묻는다.
 *
 * @param stages 파이프라인 단계별 진행 상황. 이름 · 값 모두 파이프라인 서비스가 준 그대로다
 * @param caseId 적재가 끝나면 채워진다. <b>1차 PR 범위에서는 SUCCEEDED여도 null이다</b> (적재는 2차)
 */
public record CaseImportResponse(
	Long importId,
	String status,
	List<Stage> stages,
	Long caseId,
	String errorCode,
	String message,
	Instant createdAt,
	Instant finishedAt
) {

	/** @param status PENDING · RUNNING · DONE · SKIPPED · FAILED */
	public record Stage(String name, String status) {
	}

	public static CaseImportResponse of(CaseImport entity, List<Stage> stages) {
		return new CaseImportResponse(entity.getId(), entity.getStatus().name(), stages, entity.caseId(),
			entity.getErrorCode(), entity.getMessage(), entity.getCreatedAt(), entity.getFinishedAt());
	}
}
