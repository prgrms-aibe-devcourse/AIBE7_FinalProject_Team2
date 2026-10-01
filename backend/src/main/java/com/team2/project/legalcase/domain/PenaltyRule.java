package com.team2.project.legalcase.domain;

import jakarta.persistence.Column;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 형벌별 법정형과 선고 가능 범위 (징역은 개월, 벌금은 원)
 * DEATH · LIFE 행의 allowed 범위는 작량감경해 징역으로 선고할 때의 범위다 (ERD v1.4)
 */
@Entity
@Table(name = "penalty_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PenaltyRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id")
	private LegalCase legalCase;

	@Enumerated(EnumType.STRING)
	private PenaltyType penaltyType;

	private Long statutoryMin;		// 법정형 하한 (조문에 없으면 NULL)

	private Long statutoryMax;		// 법정형 상한

	private Long allowedMin;		// 선고 가능 하한

	private Long allowedMax;		// 선고 가능 상한

	private String allowedBasis;	// 선고 가능 범위 산출 근거 (내부용, 응답하지 않음)

	@Column(name = "suspension_allowed")
	private boolean isSuspensionAllowed;	// 집행유예 입력을 보여 줄지

	private int displayOrder;

	/** 값(개월 또는 원)이 선고 가능 범위 안인지. 범위가 없으면 false */
	public boolean isWithinAllowedRange(long value) {
		return allowedMin != null && allowedMax != null
			&& allowedMin <= value && value <= allowedMax;
	}
}
