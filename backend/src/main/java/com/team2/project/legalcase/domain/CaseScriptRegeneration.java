package com.team2.project.legalcase.domain;

import jakarta.persistence.Column;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 대본 블록 재생성 작업 (BE-129 2차, API 명세 A-13-15 ~ 17). 관리자가 고른 블록을 파이프라인 서비스가 다시 만들고, 끝나면 관리자가
 * 결과를 보고 반영한다 — 사건 등록(A-14 → A-15 → A-14-1)과 같은 흐름이다.
 *
 * <p>진행 상황의 출처는 파이프라인이다. 이 행은 받아 적는 쪽이고, 끝난 작업은 다시 바꾸지 않는다({@link #applyProgress}).
 */
@Entity
@Table(name = "case_script_regeneration")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseScriptRegeneration {

	/** V47의 컬럼 길이와 같아야 한다. 넘치는 값은 저장 전에 자른다 */
	static final int ERROR_CODE_MAX = 50;
	static final int MESSAGE_MAX = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id")
	private LegalCase legalCase;

	@Column(length = 32)
	private String jobId;

	/** 관리자가 고른 블록 (단위 이름). 실제로 다시 만들 블록은 파이프라인이 연동 표로 넓힌다 */
	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> units;

	/** 그 블록을 입력으로 받는 뒤 블록도 함께 만드는지. false면 세부조정 — 뒤 블록은 낡음으로 남는다 */
	@Column(name = "cascade_later")
	private boolean cascade;

	/** 요청 당시 입력 기록. 반영할 때 지금 값과 같아야 한다({@code ScriptRegenerationInputs#snapshot}) */
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> inputs;

	@Enumerated(EnumType.STRING)
	private ScriptRegenerationStatus status;

	@Column(length = ERROR_CODE_MAX)
	private String errorCode;

	/** 실패 사유. 원문 · 모델 출력을 담지 않는다(파이프라인이 첫 줄만 보낸다) */
	@Column(length = MESSAGE_MAX)
	private String message;

	/** 마지막으로 받은 단계 상태 ([{name, status}]) — 끝난 뒤 다시 묻지 않아도 화면이 보여 줄 수 있게 */
	@JdbcTypeCode(SqlTypes.JSON)
	private List<Map<String, String>> stages;

	private Long requestedByAdminId;

	private Long appliedByAdminId;

	private Instant appliedAt;

	@CreationTimestamp
	private Instant createdAt;

	private Instant finishedAt;

	public static CaseScriptRegeneration start(LegalCase legalCase, String jobId, List<String> units, boolean cascade,
			Map<String, Object> inputs, Long adminId) {
		CaseScriptRegeneration entity = new CaseScriptRegeneration();
		entity.legalCase = legalCase;
		entity.jobId = jobId;
		entity.units = List.copyOf(units);
		entity.cascade = cascade;
		entity.inputs = new LinkedHashMap<>(inputs);
		entity.requestedByAdminId = adminId;
		return entity;
	}

	/** 파이프라인이 알려 준 진행 상황을 받아 적는다. 끝난 작업은 다시 바꾸지 않는다(사건 등록 {@code CaseImport}와 같다) */
	public void applyProgress(ScriptRegenerationStatus next, String errorCode, String message, Instant finishedAt,
			List<Map<String, String>> stages) {
		if (status != null && status.isFinished()) {
			return;
		}
		this.status = next;
		this.errorCode = limit(errorCode, ERROR_CODE_MAX);
		this.message = limit(message, MESSAGE_MAX);
		if (stages != null && !stages.isEmpty()) {
			this.stages = stages;
		}
		if (next.isFinished()) {
			this.finishedAt = finishedAt != null ? finishedAt : Instant.now();
		}
	}

	/** 아직 도는 작업인지 (첫 진행 상황을 받기 전도 도는 것으로 본다) */
	public boolean isActive() {
		return status == null || !status.isFinished();
	}

	public boolean isApplied() {
		return appliedAt != null;
	}

	/** 결과를 블록에 반영했다 */
	public void markApplied(Long adminId, Instant at) {
		if (status != ScriptRegenerationStatus.SUCCEEDED || isApplied()) {
			throw new IllegalStateException("끝나지 않았거나 이미 반영한 재생성이다");
		}
		this.appliedByAdminId = adminId;
		this.appliedAt = at;
	}

	private static String limit(String value, int max) {
		if (value == null) {
			return null;
		}
		String stripped = value.strip();
		if (stripped.isEmpty()) {
			return null;
		}
		return stripped.length() <= max ? stripped : stripped.substring(0, max - 1) + "…";
	}
}
