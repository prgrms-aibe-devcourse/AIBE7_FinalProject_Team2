package com.team2.project.legalcase.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사전 판단 형량 구간 (S-03 선택지, REQ-016)
 * V1 마이그레이션이 넣는 고정 데이터 (사기 · 상해 7개, 살인 8개)
 */
@Entity
@Table(name = "sentence_range_option")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SentenceRangeOption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	private CrimeType crimeType;

	private String label;

	@Enumerated(EnumType.STRING)
	private RangeKind kind;

	private Integer minMonths;	// 실형 구간 하한 (이상), NULL이면 제한 없음

	private Integer maxMonths;	// 실형 구간 상한 (미만), NULL이면 제한 없음

	private int displayOrder;
}
