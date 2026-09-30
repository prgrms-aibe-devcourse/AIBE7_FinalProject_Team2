package com.team2.project.legalcase.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사건별 판단 요소. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "factor")
public class Factor {

	@Id
	private Long id;

	private Long caseId;

	private String label;

	/** 사전 판단용 짧은 문구 (OVERVIEW 요소만). */
	private String preLabel;

	@Enumerated(EnumType.STRING)
	private RevealStage revealStage;

	private int displayOrder;
}
