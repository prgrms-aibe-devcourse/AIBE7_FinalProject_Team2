package com.team2.project.admin.caseimport.dto;

import com.team2.project.legalcase.domain.CourtLevel;
import com.team2.project.legalcase.domain.CrimeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 사건 등록 요청 (API 명세 A-14).
 *
 * <p>심급이 서로 달라야 한다는 조건은 애너테이션으로 적을 수 없어 서비스에서 본다.
 * 원문 길이 상한(20만 자)은 화면에서도 같은 값을 쓴다(adminMockApi.js MAX_ORIGINAL_LENGTH).
 */
public record CaseImportCreateRequest(
	@NotNull CrimeType crimeType,
	@NotNull @Size(min = 1, max = 3) List<@Valid Source> sources
) {

	public static final int MAX_ORIGINAL_TEXT_LENGTH = 200_000;

	public record Source(
		@NotNull CourtLevel courtLevel,
		@NotBlank @Size(max = 50) String caseNumber,
		@Size(max = 50) String courtName,
		LocalDate decidedAt,
		Boolean isFinal,
		@Size(max = 50) String sourceOrg,
		@NotBlank @Size(max = MAX_ORIGINAL_TEXT_LENGTH) String originalText,
		String note
	) {

		public boolean finalDecision() {
			return Boolean.TRUE.equals(isFinal);
		}
	}
}
