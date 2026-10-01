package com.team2.project.comparison.domain;

import com.team2.project.experience.domain.Experience;
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
 * (확장 단계) 세 판결 비교 분석. 체험당 1개 (FR-6-3). MVP 기능은 이 엔티티 없이 동작한다.
 * 실제 판결 공개 시 PENDING으로 만들고, 검증을 통과한 결과만 DONE으로 저장한다.
 */
@Entity
@Table(name = "comparison_analysis")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComparisonAnalysis {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "experience_id")
	private Experience experience;

	@Enumerated(EnumType.STRING)
	private AnalysisStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> content;		// 검증을 통과한 분석 (항목마다 근거 요소 ID 포함)

	@Enumerated(EnumType.STRING)
	private AnalysisFailReason failReason;

	private String modelName;

	private String promptVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> inputSnapshot;	// AI 입력 (사건 원문 · 판결문 전문이 없는지 점검용)

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> rawOutput;		// 모델 원본 출력

	@CreationTimestamp
	private Instant createdAt;					// 생성 시작 시각

	private Instant completedAt;				// DONE · FAILED가 된 시각

	/** 생성 시작 (PENDING) */
	public static ComparisonAnalysis pending(Experience experience, String modelName, String promptVersion) {
		ComparisonAnalysis analysis = new ComparisonAnalysis();
		analysis.experience = experience;
		analysis.status = AnalysisStatus.PENDING;
		analysis.modelName = modelName;
		analysis.promptVersion = promptVersion;
		return analysis;
	}

	/** 검증 통과 → DONE */
	public void complete(Map<String, Object> content, Map<String, Object> inputSnapshot,
		Map<String, Object> rawOutput, Instant now) {
		this.status = AnalysisStatus.DONE;
		this.content = content;
		this.inputSnapshot = inputSnapshot;
		this.rawOutput = rawOutput;
		this.completedAt = now;
	}

	/** 실패 → FAILED (화면은 규칙 문장 유지) */
	public void fail(AnalysisFailReason reason, Map<String, Object> inputSnapshot,
		Map<String, Object> rawOutput, Instant now) {
		this.status = AnalysisStatus.FAILED;
		this.failReason = reason;
		this.inputSnapshot = inputSnapshot;
		this.rawOutput = rawOutput;
		this.completedAt = now;
	}
}
