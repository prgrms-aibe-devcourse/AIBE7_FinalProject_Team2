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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사건별 판단 요소 목록 (사용자 · AI · 재판부 공통)
 */
@Entity
@Table(name = "factor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Factor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id")
	private LegalCase legalCase;

	private String label;		// 판단 요소 문구

	private String preLabel;	// 사전 판단용 짧은 문구 (OVERVIEW 요소만)

	@Enumerated(EnumType.STRING)
	private RevealStage revealStage;

	private String summaryTag;	// 판결 카드 한 줄 요약용 태그

	@Enumerated(EnumType.STRING)
	private ValueAxis valueAxis;	// 가치관 축 (성향 매칭용, 어느 축에도 맞지 않으면 null)

	@Enumerated(EnumType.STRING)
	private ValueAxisStatus valueAxisStatus;	// 가치관 축 후검수 상태 (AUTO · CONFIRMED)

	@JdbcTypeCode(SqlTypes.JSON)
	private ValueAxisVotes valueAxisVotes;	// 자동 분류 투표 기록 (사람 초안 · 투표 없이 정한 값은 null)

	private int displayOrder;

	/** 사전 판단 작용 요소로 고를 수 있는지 (개요 단계 요소만) */
	public boolean isSelectableInPreJudgment() {
		return revealStage == RevealStage.OVERVIEW;
	}
}
