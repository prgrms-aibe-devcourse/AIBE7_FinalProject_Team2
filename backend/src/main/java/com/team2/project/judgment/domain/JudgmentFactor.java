package com.team2.project.judgment.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 판단 요소 평가. 사전 판단은 방향 없이 고른 요소만 기록하므로 direction은 매핑하지 않는다(NULL). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "judgment_factor")
public class JudgmentFactor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long judgmentId;

	private Long factorId;

	public JudgmentFactor(Long judgmentId, Long factorId) {
		this.judgmentId = judgmentId;
		this.factorId = factorId;
	}
}
