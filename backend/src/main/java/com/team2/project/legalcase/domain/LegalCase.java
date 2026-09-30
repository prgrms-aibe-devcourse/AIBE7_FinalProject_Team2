package com.team2.project.legalcase.domain;

import java.time.OffsetDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
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

/** 사건. 사건 목록 · 체험 API가 읽는 컬럼만 매핑한다 (사건 콘텐츠는 팀이 등록하는 읽기 전용 데이터). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "legal_case")
public class LegalCase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String title;

	@Enumerated(EnumType.STRING)
	private CrimeType crimeType;

	private String shortIntro;

	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> keywords;

	@Enumerated(EnumType.STRING)
	private Difficulty difficulty;

	private Integer estimatedMinutes;

	private String thumbnailUrl;

	@Enumerated(EnumType.STRING)
	private CaseStatus status;

	@Column(name = "published_at")
	private OffsetDateTime publishedAt;
}
