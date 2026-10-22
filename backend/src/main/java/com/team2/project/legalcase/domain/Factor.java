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
	@Column(nullable = false)
	private ValueAxisStatus valueAxisStatus = ValueAxisStatus.AUTO;	// 가치관 축 후검수 상태 (AUTO · CONFIRMED). NOT NULL이라 새 행도 AUTO로 시작한다

	// 파이프라인(SQL)이 관리하는 원본은 UPDATE에서 제외한다. 읽을 때 무시한 키가 지워지는 것을 막는다 (BE-81).
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(updatable = false)
	private ValueAxisVotes valueAxisVotes;	// 자동 분류 투표 기록 (사람 초안 · 투표 없이 정한 값은 null)

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(updatable = false)
	private SummaryTagCandidates summaryTagCandidates;	// 요약어 후보 (BE-80). 후보 계산을 켜지 않은 적재 · 사람 초안은 null

	private int displayOrder;

	/** 관리자가 가치관 축을 확정한다 (A-12). null = 어느 축에도 맞지 않음으로 확정. 투표 기록은 그대로 둔다 */
	public void confirmValueAxis(ValueAxis valueAxis) {
		this.valueAxis = valueAxis;
		this.valueAxisStatus = ValueAxisStatus.CONFIRMED;
	}

	/** 사전 판단 작용 요소로 고를 수 있는지 (개요 단계 요소만) */
	public boolean isSelectableInPreJudgment() {
		return revealStage == RevealStage.OVERVIEW;
	}

	/**
	 * 적재(BE-93 2차).
	 *
	 * <p><b>displayOrder는 사건 입력의 factorId를 그대로 쓴다.</b> 파이프라인의 세 생성기가 모두 factorId를
	 * display_order로 조인하므로(판결의 judgment_factor도 이 값으로 요소를 찾는다) 바꾸면 연결이 끊긴다.
	 *
	 * <p>valueAxisStatus는 AUTO로 시작한다 — 자동 분류 결과이고, 관리자가 A-12에서 확정한다.
	 */
	public static Factor of(LegalCase legalCase, String label, String preLabel, RevealStage revealStage,
			String summaryTag, ValueAxis valueAxis, ValueAxisVotes valueAxisVotes, int displayOrder) {
		Factor entity = new Factor();
		entity.legalCase = legalCase;
		entity.label = label;
		entity.preLabel = preLabel;
		entity.revealStage = revealStage;
		entity.summaryTag = summaryTag;
		entity.valueAxis = valueAxis;
		entity.valueAxisVotes = valueAxisVotes;
		entity.valueAxisStatus = ValueAxisStatus.AUTO;
		entity.displayOrder = displayOrder;
		return entity;
	}
}
