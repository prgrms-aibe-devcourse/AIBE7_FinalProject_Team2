package com.team2.project.legalcase.domain;

import com.team2.project.judgment.domain.ReviewStatus;
import jakarta.persistence.Column;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * 사건 대본 (BE-108). 사건마다 1건. BE-107이 파이프라인 {@code script} 단계에서 만든 증거조사 대사를 담는다.
 *
 * <p><b>증거조사 대사뿐이다.</b> 1막 기사 해설 · 피고인신문 · 최종의견은 파이프라인 범위 밖이라
 * (COMMON-65 · FE-33) 아직 생성되지 않는다. 그래서 칸 이름도 {@code acts}가 아니라 {@code evidence}다.
 *
 * <p>내용을 jsonb 한 칸에 두는 이유는 {@code PipelineResult}와 같다 — 형식을 정하는 쪽은 파이프라인이고,
 * 서버는 대본 안을 들여다보지 않는다(검색 · 집계 대상이 아니다). 그래서 대사 한 줄을 레코드로 받아 적지
 * 않고 {@code Map} 그대로 둔다({@code ai_generation.raw_output}과 같은 방식).
 *
 * <p>검수는 판결 후보({@code Judgment})와 같은 방식이다: {@code APPROVED} · {@code REJECTED}만 저장하고
 * 비어 있으면 대기로 읽는다. <b>사건 공개와 대본 공개는 별개다</b> — 사건이 {@code PUBLISHED}여도 대본은
 * 승인 전까지 내려가지 않는다(BE-109). 대본이 늦게 들어와도 사건 공개를 막지 않기 위해서다.
 */
@Entity
@Table(name = "case_script")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseScript {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id")
	private LegalCase legalCase;

	/** 판단 요소별 대사 배열 (파이프라인 evidence.json의 evidence) */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private List<Map<String, Object>> evidence;

	private String model;

	/** 파이프라인이 대본을 만든 시각. 적재 시각({@link #createdAt})과 다르다 — 생성은 적재보다 먼저다 */
	private Instant generatedAt;

	/** 호출 한도로 중간에 멈췄는지. {@code publishable}이 false일 때 생성 실패와 중단을 구분한다 */
	private boolean stopped;

	/** 배역 {@code {prosecutor, defense}}. cast는 SQL 예약어라 칸 이름은 cast_roles다 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "cast_roles")
	private Map<String, Object> castRoles;

	/** 검사 · 변호인 분량이 치우쳤다는 경고. 자동으로 고치지 않고 관리자가 보고 판단한다(BE-107) */
	private String balanceWarning;

	/**
	 * 파이프라인이 공개해도 된다고 본 결과인지. 생성 실패 · 호출 한도 중단이면 증거 카드와 대사가 1:1로
	 * 어긋나 {@code false}다(BE-107 PR #277 리뷰 7번). 이 값이 {@code false}면 승인할 수 없다 —
	 * 다시 생성해야 한다. DB에도 같은 제약이 있다(chk_case_script_publishable_approved)
	 */
	private boolean publishable;

	@Enumerated(EnumType.STRING)
	private ReviewStatus reviewStatus;		// APPROVED · REJECTED만 저장한다

	private Long reviewedByAdminId;

	private Instant reviewedAt;

	private String reviewNote;				// 반려 사유 (REJECTED일 때만)

	@CreationTimestamp
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

	public static CaseScript of(LegalCase legalCase, List<Map<String, Object>> evidence, String model,
			Map<String, Object> castRoles, String balanceWarning, boolean publishable, Instant generatedAt,
			boolean stopped) {
		CaseScript script = new CaseScript();
		script.legalCase = legalCase;
		script.evidence = List.copyOf(evidence);
		script.model = model;
		script.generatedAt = generatedAt;
		script.stopped = stopped;
		// Map.copyOf는 null 값을 허용하지 않는다. 배역 한쪽이 비어 오면(파이프라인이 한 측만 채운 경우)
		// 적재 전체가 NPE로 막히므로 새 맵에 그대로 담는다
		script.castRoles = castRoles == null ? null : new LinkedHashMap<>(castRoles);
		script.balanceWarning = balanceWarning;
		script.publishable = publishable;
		return script;
	}

	/** 검수 화면의 상태. 기록이 있으면 그 값, 없으면 대기 */
	public ReviewStatus reviewState() {
		return reviewStatus == null ? ReviewStatus.PENDING : reviewStatus;
	}

	/** 공개할 수 있는 대본인지 (BE-109 서빙 기준). 사건 공개 여부와는 별개다 */
	public boolean isPublic() {
		return reviewStatus == ReviewStatus.APPROVED;
	}

	/** 승인 — 이 대본을 공개한다 (A-13-11). 파이프라인이 어긋난다고 표시한 대본은 승인할 수 없다 */
	public void approve(Long adminId, Instant at) {
		if (!publishable) {
			throw new IllegalStateException("증거 카드와 대사가 어긋난 대본은 승인할 수 없다 — 다시 생성해야 한다");
		}
		this.reviewStatus = ReviewStatus.APPROVED;
		this.reviewedByAdminId = adminId;
		this.reviewedAt = at;
		this.reviewNote = null;
	}

	/** 반려 (A-13-12). 반려한 대본은 공개되지 않는다 */
	public void reject(Long adminId, Instant at, String note) {
		this.reviewStatus = ReviewStatus.REJECTED;
		this.reviewedByAdminId = adminId;
		this.reviewedAt = at;
		this.reviewNote = note;
	}

	/**
	 * 대사 한 줄을 관리자가 직접 고친다 (A-13-13). 숫자 하나 · 말투 때문에 모델을 다시 부르는 것보다 싸다.
	 *
	 * <p>고친 줄은 {@code validation.manual = true}로 표시하고 {@code errors}를 비운다 — 모델이 실패로 남긴
	 * 줄이라도 사람이 채웠으면 더는 실패가 아니다. {@code warnings}는 남긴다(사람이 보고 판단할 값이다).
	 *
	 * <p>고치면 <b>검수 기록을 지운다</b>. 내용이 바뀌었는데 지난 승인이 남아 있으면 검수하지 않은 대사가
	 * 공개된다.
	 *
	 * @return 그 요소의 대사를 찾아 고쳤으면 true, 대본에 그 요소가 없으면 false
	 */
	public boolean editLine(int factorId, String text, Instant at) {
		List<Map<String, Object>> edited = new ArrayList<>();
		boolean found = false;
		for (Map<String, Object> line : evidence) {
			Map<String, Object> copy = new LinkedHashMap<>(line);
			if (!found && factorIdOf(line) == factorId) {
				copy.put("text", text);
				copy.put("validation", manualValidation(line.get("validation")));
				copy.put("editedAt", at.toString());
				found = true;
			}
			edited.add(copy);
		}
		if (!found) {
			return false;
		}
		this.evidence = List.copyOf(edited);
		this.publishable = recomputePublishable();
		this.reviewStatus = null;		// 고친 대본은 다시 검수 대기로
		this.reviewedByAdminId = null;
		this.reviewedAt = null;
		this.reviewNote = null;
		return true;
	}

	/**
	 * 파이프라인과 <b>같은 식</b>을 다시 적용한다 — {@code publishable = not stopped and not failed}
	 * (`pipeline.py` stage_script). 사람이 고쳐 빈 대사가 사라지면 승인할 수 있게 올라가고, 호출 한도로
	 * 멈춘 대본은 <b>줄 자체가 없어</b> 고쳐도 올라가지 않는다(그때는 다시 생성해야 한다).
	 */
	private boolean recomputePublishable() {
		if (stopped) {
			return false;
		}
		return evidence.stream().allMatch(line -> {
			Object text = line.get("text");
			return text instanceof String value && !value.isBlank();
		});
	}

	/** 저장된 JSON의 숫자는 Integer · Long 어느 쪽으로도 올 수 있다 */
	private static int factorIdOf(Map<String, Object> line) {
		return line.get("factorId") instanceof Number number ? number.intValue() : -1;
	}

	/** 사람이 고친 줄의 검증 기록. 경고는 남기고 실패만 지운다 */
	private static Map<String, Object> manualValidation(Object previous) {
		Map<String, Object> validation = new LinkedHashMap<>();
		if (previous instanceof Map<?, ?> before && before.get("warnings") instanceof List<?> warnings) {
			validation.put("warnings", List.copyOf(warnings));
		} else {
			validation.put("warnings", List.of());
		}
		validation.put("ok", true);
		validation.put("manual", true);		// 화면이 "사람이 고침"으로 표시한다
		return validation;
	}
}
