package com.team2.project.judgment.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * (확장 단계) AI 판결 생성 기록 (REQ-047 · 079). MVP 기능은 이 엔티티 없이 동작한다.
 * 모델 · 프롬프트가 바뀌면 새 judgment와 새 행을 만들고 기존 행은 지우지 않는다.
 */
@Entity
@Table(name = "ai_generation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiGeneration {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "judgment_id")
	private Judgment judgment;

	private String modelName;

	private String promptVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> inputSnapshot;	// AI 입력 (실제 판결이 없는지 검증용)

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> rawOutput;		// 모델 원본 출력

	@Enumerated(EnumType.STRING)
	private ReviewStatus reviewStatus;

	private String reviewedBy;

	private Instant reviewedAt;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> generationReport;	// 자동 생성 정보 (검증 경고 · 사전 학습 점검 · 회차 선택, BE-31 후검수용)

	@CreationTimestamp
	private Instant createdAt;

	/**
	 * 관리자 검수 결과를 기존 도구가 읽는 칸에도 맞춘다 (V16 이후 원본은 judgment.review_status).
	 * reviewed_by(varchar 50)는 관리자 이메일이 들어가지 않을 수 있어 건드리지 않는다(API 명세 8-3 #5)
	 */
	public void syncReview(ReviewStatus status, Instant at) {
		this.reviewStatus = status;
		this.reviewedAt = at;
	}

	/**
	 * 적재(BE-93 2차). 관리자 후검수를 기다리는 상태로 만든다.
	 *
	 * <p>{@code reviewStatus = PENDING}이고 검토자 · 검토 시각은 비운다 — 관리자가 A-9에서 승인하면 채워진다.
	 *
	 * @param inputSnapshot    AI에 넣은 프롬프트 (실제 판결이 섞이지 않았는지 확인용)
	 * @param rawOutput        모델이 돌려준 판결 원본
	 * @param generationReport 어느 모델 · 몇 번째 회차를 왜 골랐는지와 검증 경고 (관리자 후검수용)
	 */
	public static AiGeneration pending(Judgment judgment, String modelName, String promptVersion,
			Map<String, Object> inputSnapshot, Map<String, Object> rawOutput,
			Map<String, Object> generationReport) {
		AiGeneration entity = new AiGeneration();
		entity.judgment = judgment;
		entity.modelName = modelName;
		entity.promptVersion = promptVersion;
		entity.inputSnapshot = inputSnapshot;
		entity.rawOutput = rawOutput;
		entity.generationReport = generationReport;
		entity.reviewStatus = ReviewStatus.PENDING;
		return entity;
	}
}
