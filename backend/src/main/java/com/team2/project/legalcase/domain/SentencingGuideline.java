package com.team2.project.legalcase.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 양형기준 버전 (사건별 적용 버전 기록용, REQ-080)
 * 팀이 SQL로 등록하는 기준 데이터라 생성 메서드를 두지 않는다.
 */
@Entity
@Table(name = "sentencing_guideline")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SentencingGuideline {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String crimeCategory;	// 예: 살인범죄

	private String versionName;		// 예: 2024 개정

	private LocalDate effectiveDate;	// 시행일

	private String sourceUrl;
}
