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
}
