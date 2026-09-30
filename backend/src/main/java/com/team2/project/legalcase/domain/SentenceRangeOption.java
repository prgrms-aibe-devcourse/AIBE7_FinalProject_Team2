package com.team2.project.legalcase.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사전 판단 형량 구간 (범죄 유형별 고정 데이터, S-03 선택지). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "sentence_range_option")
public class SentenceRangeOption {

	@Id
	private Long id;

	@Enumerated(EnumType.STRING)
	private CrimeType crimeType;

	private String label;

	private int displayOrder;
}
