package com.team2.project.judgment.domain;

import com.team2.project.legalcase.domain.Factor;
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
 * 판단 요소 평가. 행이 없으면 "고려하지 않음"(—)으로 본다 (ERD 결정 #1)
 */
@Entity
@Table(name = "judgment_factor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JudgmentFactor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "judgment_id")
	private Judgment judgment;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "factor_id")
	private Factor factor;

	@Enumerated(EnumType.STRING)
	private Direction direction;	// 사전 판단(PRE)은 NULL

	private String evidence;		// 재판부 판단의 근거 문장 (COURT)

	private JudgmentFactor(Judgment judgment, Factor factor, Direction direction) {
		this.judgment = judgment;
		this.factor = factor;
		this.direction = direction;
	}

	/** 사전 판단 작용 요소 (방향 없음, 확장 REQ-093) */
	public static JudgmentFactor forPre(Judgment judgment, Factor factor) {
		return new JudgmentFactor(judgment, factor, null);
	}

	/** 최종 판결 판단 요소 (방향 필수) */
	public static JudgmentFactor forFinal(Judgment judgment, Factor factor, Direction direction) {
		if (direction == null) {
			throw new InvalidJudgmentException(InvalidJudgmentException.Reason.MISSING_DIRECTION,
				"최종 판결의 판단 요소에는 방향이 필요합니다.");
		}
		return new JudgmentFactor(judgment, factor, direction);
	}
}
