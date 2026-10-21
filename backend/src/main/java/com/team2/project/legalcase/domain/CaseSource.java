package com.team2.project.legalcase.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 원본 판결문 (내부 전용)
 * sourceOrg 외의 값(사건번호 · 법원명 · 선고일 · 원문)은 사용자 응답에 절대 넣지 않는다 (FR-5-3)
 */
@Entity
@Table(name = "case_source")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseSource {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id")
	private LegalCase legalCase;

	@Enumerated(EnumType.STRING)
	private CourtLevel courtLevel;

	private String caseNumber;

	private String courtName;

	private LocalDate decidedAt;

	private boolean isFinal;	// 최종 확정 판결 여부

	private String sourceOrg;		// 출처 기관 (사용자에게 노출 가능한 유일한 값)

	private String originalText;

	private String note;

	/**
	 * 적재(BE-93 2차). 등록 요청에 들어온 판결문을 사건에 붙인다.
	 *
	 * <p>원문은 파이프라인이 돌려주지 않아(FR-5-3) 백엔드가 case_import_source에 들고 있던 값을 옮긴다.
	 */
	public static CaseSource of(LegalCase legalCase, CourtLevel courtLevel, String caseNumber, String courtName,
			LocalDate decidedAt, boolean isFinal, String sourceOrg, String originalText, String note) {
		CaseSource entity = new CaseSource();
		entity.legalCase = legalCase;
		entity.courtLevel = courtLevel;
		entity.caseNumber = caseNumber;
		entity.courtName = courtName;
		entity.decidedAt = decidedAt;
		entity.isFinal = isFinal;
		entity.sourceOrg = sourceOrg;
		entity.originalText = originalText;
		entity.note = note;
		return entity;
	}
}
