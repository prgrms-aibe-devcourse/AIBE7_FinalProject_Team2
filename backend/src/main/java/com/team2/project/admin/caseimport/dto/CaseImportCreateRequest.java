package com.team2.project.admin.caseimport.dto;

import com.team2.project.legalcase.domain.CourtLevel;
import com.team2.project.legalcase.domain.CrimeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * 사건 등록 요청 (API 명세 A-14).
 *
 * <p>심급이 서로 달라야 한다는 조건과 최종 확정 판결이 1건이어야 한다는 조건은 애너테이션으로 적을 수 없어
 * 서비스에서 본다. 원문 길이 상한(20만 자)은 화면에서도 같은 값을 쓴다(adminMockApi.js MAX_ORIGINAL_LENGTH).
 *
 * <p>원소의 {@code @NotNull}이 필요한 이유: Bean Validation은 <b>null 원소를 건너뛴다</b>. 없으면
 * {@code {"sources":[null]}}이 검증을 통과해 서비스에서 NPE로 터지고, 400이어야 할 응답이 500이 된다
 * (PR #232 CodeRabbit 리뷰).
 */
public record CaseImportCreateRequest(
	@NotNull CrimeType crimeType,
	@NotNull @Size(min = 1, max = 3) List<@NotNull @Valid Source> sources,
	SpecialMode specialMode
) {

	public static final int MAX_ORIGINAL_TEXT_LENGTH = 200_000;

	public CaseImportCreateRequest(CrimeType crimeType, List<Source> sources) {
		this(crimeType, sources, null);
	}

	/**
	 * 특별 모드 (BE-165). 비우면 꺼짐 — 검사 오류가 있으면 등록이 실패한다. 켜면 검사 오류 일부를 경고로 낮춰 적재까지 진행하고,
	 * 낮춘 오류는 ai_generation.generation_report(validationWarnings · specialMode)에 남아 관리자가 원 판결문과 대조해 검수한다.
	 * 재판부 · AI 판결은 지금처럼 비공개로 들어가므로 관리자가 공개하기 전에는 사용자에게 보이지 않는다.
	 * 개인정보 최소선(주민등록번호 · 전화번호 등) · DB 제약 오류는 어느 모드든 막는다(tools/ai-judgment/special_mode.py)
	 */
	public enum SpecialMode {
		/** 형식 오류만 낮춘다: 원문 인용 대조 · 상세 주소 · 정확한 날짜 · 정확한 나이 */
		FORMAT,
		/** FORMAT에 더해 내용 오류(평가 표현 · 재판부가 고려한 요소 없음)도 낮춘다 */
		ALL;

		/** 파이프라인 서비스 값 (format · all) */
		public String pipelineValue() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

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
