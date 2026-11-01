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
import jakarta.persistence.ManyToOne;
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
 * 대본 단위 블록 (BE-126). 대본({@link CaseScript})마다 단위(U0 ~ U4) 하나에 한 행이다.
 *
 * <p>내용({@link #content})은 파이프라인이 만든 모양 그대로다 — U3는 증거조사 대사 배열, 나머지는 객체. 검수 결과의
 * {@code path}(JSON Pointer)가 이 값을 가리키므로 화면용 모양으로 바꾸지 않는다(형식: `prompts/script/README.md` "저장 블록").
 *
 * <p>{@link #review} · {@link #warnings} · {@link #repairs}는 <b>관리자 화면 전용</b>이다. 고치기 전 문장과 대본 인용이 들어
 * 있어 공개 API(API 18)에는 싣지 않는다.
 */
@Entity
@Table(name = "case_script_block")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseScriptBlock {

	private static final String ERROR = "error";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "script_id")
	private CaseScript script;

	@Enumerated(EnumType.STRING)
	private ScriptUnit unit;

	/** 블록 값. U3는 {@code List<Map>}, 나머지는 {@code Map}이다 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private Object content;

	/** 판. 값이 바뀔 때마다 오른다 — 뒤 블록의 {@code basis}가 이 번호로 "어느 판을 보고 만들었는지" 남긴다 */
	private int revision;

	/** 만들 때 본 값 ({@link ScriptUnit#basis()}). 지금 값과 다르면 낡은 블록이다(계산 · 표시는 2차) */
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> basis;

	/** 검수 조건 결과(BE-122) 중 이 블록 것. {@code severity: error}가 있으면 승인할 수 없다 */
	@JdbcTypeCode(SqlTypes.JSON)
	private List<Map<String, Object>> review;

	/** 단위 형식 경고 (U3 경고는 줄마다 {@code validation}에 있다) */
	@JdbcTypeCode(SqlTypes.JSON)
	private List<Object> warnings;

	/** 고쳐 쓰기 기록(BE-122 2차) 중 이 블록 것 */
	@JdbcTypeCode(SqlTypes.JSON)
	private List<Map<String, Object>> repairs;

	/** 오류 없이 공개할 수 있는 블록인지. DB에도 "승인하려면 true" 제약이 있다(chk_case_script_block_publishable_approved) */
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

	static CaseScriptBlock of(CaseScript script, ScriptUnit unit, Object content, int revision,
			Map<String, Object> basis, List<Map<String, Object>> review, List<Object> warnings,
			List<Map<String, Object>> repairs) {
		CaseScriptBlock block = new CaseScriptBlock();
		block.script = script;
		block.unit = unit;
		block.content = content;
		block.revision = Math.max(revision, 1);
		// List.copyOf · Map.copyOf는 null 값을 허용하지 않는다 — basis에는 null이 들어간다(모르는 DB 판)
		block.basis = basis == null ? new LinkedHashMap<>() : new LinkedHashMap<>(basis);
		block.review = review == null ? new ArrayList<>() : new ArrayList<>(review);
		block.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
		block.repairs = repairs == null ? new ArrayList<>() : new ArrayList<>(repairs);
		block.publishable = block.computePublishable();
		return block;
	}

	public ReviewStatus reviewState() {
		return reviewStatus == null ? ReviewStatus.PENDING : reviewStatus;
	}

	public boolean isApproved() {
		return reviewStatus == ReviewStatus.APPROVED;
	}

	/** U3 대사 목록. 다른 단위거나 모양이 다르면 빈 목록 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> lines() {
		if (unit != ScriptUnit.U3 || !(content instanceof List<?> items)) {
			return List.of();
		}
		return items.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
	}

	void approve(Long adminId, Instant at) {
		if (!publishable) {
			throw new IllegalStateException("오류가 있는 블록은 승인할 수 없다: " + unit);
		}
		record(ReviewStatus.APPROVED, adminId, at, null);
	}

	void reject(Long adminId, Instant at, String note) {
		record(ReviewStatus.REJECTED, adminId, at, note);
	}

	/** 검수 기록을 지운다 — 내용이 바뀌었는데 지난 승인이 남아 있으면 검수하지 않은 대사가 공개된다 */
	void clearReview() {
		record(null, null, null, null);
	}

	private void record(ReviewStatus status, Long adminId, Instant at, String note) {
		this.reviewStatus = status;
		this.reviewedByAdminId = adminId;
		this.reviewedAt = at;
		this.reviewNote = note;
	}

	/**
	 * U3 대사 한 줄을 관리자가 직접 고친다 (A-13-13). 고친 줄은 {@code validation.manual = true}로 표시하고 실패를 지운다 —
	 * 모델이 실패로 남긴 줄이라도 사람이 채웠으면 더는 실패가 아니다. 경고는 남긴다(사람이 보고 판단할 값이다).
	 * 그 줄의 검수 조건 오류(BE-122)도 같은 이유로 지운다. 값이 바뀌었으니 판이 오른다.
	 *
	 * @return 그 요소의 대사를 찾아 고쳤으면 true
	 */
	boolean editLine(int factorId, String text, Instant at) {
		List<Map<String, Object>> edited = new ArrayList<>();
		boolean found = false;
		for (Map<String, Object> line : lines()) {
			Map<String, Object> copy = new LinkedHashMap<>(line);
			if (!found && factorIdOf(line.get("factorId")) == factorId) {
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
		this.content = edited;
		this.review = new ArrayList<>(review.stream()
			.filter(issue -> !(ERROR.equals(issue.get("severity")) && factorIdOf(issue.get("factorId")) == factorId))
			.toList());
		this.revision++;
		this.publishable = computePublishable();
		return true;
	}

	/**
	 * 오류 없이 공개할 수 있는지. 검수 조건 오류가 없어야 하고, U3는 대사가 하나 이상이며 모든 줄이 채워져 있어야 한다
	 * (파이프라인 {@code publishable}의 대사 조건과 같다 — `script_assembler.build_script`)
	 */
	private boolean computePublishable() {
		if (content == null || review.stream().anyMatch(issue -> ERROR.equals(issue.get("severity")))) {
			return false;
		}
		if (unit != ScriptUnit.U3) {
			return true;
		}
		List<Map<String, Object>> lines = lines();
		return !lines.isEmpty() && lines.stream()
			.allMatch(line -> line.get("text") instanceof String value && !value.isBlank());
	}

	/** 저장된 JSON의 숫자는 Integer · Long 어느 쪽으로도 올 수 있다 */
	static int factorIdOf(Object value) {
		return value instanceof Number number ? number.intValue() : -1;
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
