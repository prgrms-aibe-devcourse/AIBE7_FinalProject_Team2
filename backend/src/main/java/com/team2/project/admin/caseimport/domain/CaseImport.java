package com.team2.project.admin.caseimport.domain;

import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.LegalCase;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사건 등록 작업 (BE-93, API 명세 A-14 · A-15).
 *
 * <p>관리자가 판결문을 넣으면 백엔드가 파이프라인 서비스(COMMON-50)에 작업을 맡기고, 이 행으로 추적한다.
 * 파이프라인은 수십 분 걸려서 요청을 기다리지 않고(202) 화면이 2초마다 A-15로 묻는다.
 *
 * <p>진행 상황의 출처는 <b>파이프라인의 state.json</b>이다. 이 행은 그 결과를 받아 적는 쪽이고,
 * 끝난 뒤에야 status · finishedAt이 확정된다 (COMMON-50과 같은 원칙 — 상태를 두 군데서 세지 않는다).
 */
@Entity
@Table(name = "case_import")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseImport {

	/** V24의 컬럼 길이와 같아야 한다. 넘치는 값은 저장 전에 자른다 ({@link #applyProgress}) */
	static final int JOB_ID_MAX = 32;
	static final int ERROR_CODE_MAX = 50;
	static final int MESSAGE_MAX = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 파이프라인 서비스의 작업 ID (32자리 16진수) */
	@Column(length = JOB_ID_MAX)
	private String jobId;

	@Enumerated(EnumType.STRING)
	private CaseImportStatus status;

	@Enumerated(EnumType.STRING)
	private CrimeType crimeType;

	/** 적재가 끝나면 채워진다 (2차 PR). 1차 범위에서는 SUCCEEDED여도 null이다 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id")
	private LegalCase legalCase;

	@Column(length = ERROR_CODE_MAX)
	private String errorCode;

	/**
	 * 실패 사유. 원문 · 모델 출력을 담지 않는다 (API 명세 A-15).
	 *
	 * <p>파이프라인이 주는 값이라 길이를 보장할 수 없어 {@link #applyProgress}에서 잘라 넣는다. 자르지 않으면
	 * 긴 실패 사유 하나가 컬럼 길이를 넘겨 저장에 실패하고, <b>상태 조회와 새 등록이 모두 500이 된다</b>
	 * (PR 리뷰 지적).
	 */
	@Column(length = MESSAGE_MAX)
	private String message;

	/**
	 * 파이프라인에서 마지막으로 받은 단계 상태 ([{name, status}, ...]).
	 *
	 * <p>단계별 상태를 아는 쪽은 파이프라인뿐인데, 작업이 끝나면 더 묻지 않고(또는 작업이 지워져 물을 수
	 * 없고) 화면을 새로 열 수 있다. 그때 "모두 PENDING"처럼 사실과 다른 값을 보여주지 않으려고 받아 둔다.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	private List<Map<String, String>> stages;

	@CreationTimestamp
	private Instant createdAt;

	private Instant finishedAt;

	@OneToMany(mappedBy = "caseImport", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("displayOrder asc")
	private List<CaseImportSource> sources = new ArrayList<>();

	/**
	 * 작업 행을 만든다. 상태는 아직 비워 두고 {@link #applyProgress}로 한 번에 적는다.
	 *
	 * <p>여기서 상태를 먼저 넣으면, 파이프라인이 처음부터 FAILED를 돌려준 경우 applyProgress가
	 * "끝난 작업은 건드리지 않는다" 규칙에 걸려 <b>실패 사유 · 단계 상태를 하나도 남기지 못한다</b>
	 * (PR 리뷰 지적).
	 */
	public static CaseImport start(String jobId, CrimeType crimeType) {
		CaseImport entity = new CaseImport();
		entity.jobId = jobId;
		entity.crimeType = crimeType;
		return entity;
	}

	/** 요청에 들어온 판결문을 순서대로 붙인다 */
	public void addSource(CaseImportSource source) {
		source.assignTo(this, sources.size());
		sources.add(source);
	}

	/**
	 * 파이프라인이 알려 준 진행 상황을 받아 적는다.
	 *
	 * <p>끝난 작업은 다시 바꾸지 않는다 — 한 번 SUCCEEDED · FAILED로 끝난 작업이 나중 조회 때문에
	 * 뒤집히면 화면이 이미 넘어간 뒤에 상태가 달라진다.
	 */
	public void applyProgress(CaseImportStatus next, String errorCode, String message, Instant finishedAt,
			List<Map<String, String>> stages) {
		if (status != null && status.isFinished()) {
			return;
		}
		this.status = next;
		this.errorCode = limit(errorCode, ERROR_CODE_MAX);
		this.message = limit(message, MESSAGE_MAX);
		if (stages != null && !stages.isEmpty()) {
			this.stages = stages;	// 못 받았으면 마지막으로 받은 값을 그대로 둔다
		}
		if (next.isFinished()) {
			this.finishedAt = finishedAt != null ? finishedAt : Instant.now();
		}
	}

	public Long caseId() {
		return legalCase == null ? null : legalCase.getId();
	}

	/** 컬럼 길이를 넘는 값을 잘라 넣는다. 잘렸다는 것이 보이게 끝에 … 를 붙인다 */
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
