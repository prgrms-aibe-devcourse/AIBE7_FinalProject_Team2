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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * 사건 (case는 SQL 예약어라 legal_case)
 * MVP에는 관리자 화면이 없어 팀이 SQL로 등록하므로 생성 메서드를 두지 않는다.
 */
@Entity
@Table(name = "legal_case")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalCase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String title;

	@Enumerated(EnumType.STRING)
	private CrimeType crimeType;

	private String chargeName;			// 죄명 (예: 살인)

	private String shortIntro;			// 목록 카드용 짧은 소개

	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> keywords;		// 목록 카드용 중립 키워드

	@Enumerated(EnumType.STRING)
	private Difficulty difficulty;

	private Integer estimatedMinutes;

	private String overview;			// S-03 사건 개요 (섹션 ①)

	private String thumbnailUrl;		// 없으면 화면이 범죄 유형별 기본 이미지 사용

	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> deidentifiedItems;	// (확장) 비식별화한 항목 종류

	private String appliedLaw;			// 적용 법조문 (고정 입력값)

	private String statutoryPenaltyText;	// 법정형 안내 문구

	private Integer recommendedMinMonths;	// 권고 형량 하한 (개월)

	private Integer recommendedMaxMonths;	// 권고 형량 상한 (개월)

	private String recommendedBasis;		// 권고 범위 산출 근거

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "guideline_id")
	private SentencingGuideline guideline;	// 적용 양형기준 버전

	private LocalDate incidentDate;			// 사건 발생일

	@Enumerated(EnumType.STRING)
	private CaseStatus status;

	private Instant publishedAt;

	@CreationTimestamp
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

	/**
	 * 관리자가 처음 손대면 DRAFT → REVIEW (API 명세 8-2, 8-3 #3). 이미 REVIEW · PUBLISHED면 그대로 둔다.
	 * 되돌리는 경로(REVIEW → DRAFT)는 두지 않는다
	 */
	public void startReview() {
		if (status == CaseStatus.DRAFT) {
			status = CaseStatus.REVIEW;
		}
	}

	/** 공개 (A-13). 공개 조건 확인은 호출하는 쪽(관리자 서비스)이 한다 */
	public void publish(Instant at) {
		this.status = CaseStatus.PUBLISHED;
		this.publishedAt = at;
	}

	// 관리자 사건 내용 수정 (A-11). 값 검사는 AdminCaseEditService가 API 명세 A-11 표대로 먼저 한다
	public void changeTitle(String title) {
		this.title = title;
	}

	public void changeShortIntro(String shortIntro) {
		this.shortIntro = shortIntro;
	}

	public void changeOverview(String overview) {
		this.overview = overview;
	}

	public void changeKeywords(List<String> keywords) {
		this.keywords = List.copyOf(keywords);
	}

	public void changeDifficulty(Difficulty difficulty) {
		this.difficulty = difficulty;
	}

	public void changeEstimatedMinutes(Integer estimatedMinutes) {
		this.estimatedMinutes = estimatedMinutes;
	}

	public void changeThumbnailUrl(String thumbnailUrl) {
		this.thumbnailUrl = thumbnailUrl;
	}

	public void changeRecommendedRange(Integer minMonths, Integer maxMonths) {
		this.recommendedMinMonths = minMonths;
		this.recommendedMaxMonths = maxMonths;
	}

	public void changeRecommendedBasis(String recommendedBasis) {
		this.recommendedBasis = recommendedBasis;
	}

	/** 사용자에게 노출되는 사건인지 */
	public boolean isPublished() {
		return status == CaseStatus.PUBLISHED;
	}

	/** jsonb 배열 컬럼은 비어 있으면 NULL로 들어온다. 응답에서 []로 내려가도록 여기서 한 번만 맞춘다 (API 12) */
	public List<String> getDeidentifiedItems() {
		return deidentifiedItems == null ? List.of() : deidentifiedItems;
	}
}
