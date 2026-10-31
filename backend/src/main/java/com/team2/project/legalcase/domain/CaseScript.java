package com.team2.project.legalcase.domain;

import com.team2.project.judgment.domain.ReviewStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * 사건 대본 (BE-108, 블록 저장은 BE-126). 사건마다 1건. 내용은 단위 블록({@link CaseScriptBlock}, U0 ~ U4)에 있고
 * 여기에는 대본 전체 값(메타 · 생성 정보 · 공개 가능 여부)만 둔다.
 *
 * <p>증거조사 대사만 만든 대본(BE-107, {@code stages.script.full = false})은 U3 블록 하나다. 대본 전체(BE-121)는
 * 만든 단위만큼 블록이 있고, 만들지 못한 단위는 {@link #pending}에 남는다.
 *
 * <p><b>검수 상태는 블록에 있다.</b> 대본의 상태는 블록에서 계산한다 — 모든 블록이 승인됐으면 공개({@link #isPublic()}).
 * 1차는 통승인 · 통거절이라 모든 블록에 한 번에 적용하고(A-13-11 · 12), 블록 단위 승인은 2차다.
 * <b>사건 공개와 대본 공개는 별개다</b> — 사건이 {@code PUBLISHED}여도 대본은 승인 전까지 내려가지 않는다(BE-109).
 */
@Entity
@Table(name = "case_script")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseScript {

	private static final String ERROR = "error";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id")
	private LegalCase legalCase;

	@OneToMany(mappedBy = "script", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("unit")
	private List<CaseScriptBlock> blocks = new ArrayList<>();

	private String model;

	/** 파이프라인이 대본을 만든 시각. 적재 시각({@link #createdAt})과 다르다 — 생성은 적재보다 먼저다 */
	private Instant generatedAt;

	/** 호출 한도로 중간에 멈췄는지. {@code publishable}이 false일 때 생성 실패와 중단을 구분한다 */
	private boolean stopped;

	/** 배역 {@code {prosecutor, defense}} (메타 cast). cast는 SQL 예약어라 칸 이름은 cast_roles다 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "cast_roles")
	private Map<String, Object> castRoles;

	/** 분류 쟁점 (메타 issue). 프론트 공통 틀이 신문 마지막 질문을 고른다. 분류 없이 만든 대본은 null */
	private String issue;

	/** 피고인이 혐의를 인정하는지 (메타 admits, U0 세 번째 답). U0가 없으면 null */
	private Boolean admits;

	/** 파이프라인이 만들지 못한 단위. 하나라도 있으면 대본 전체를 승인할 수 없다 */
	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> pending;

	/** 대본 전체에 걸린 검수 결과 ({@code REVIEW_FAILED} — 검사 자체가 돌지 못함). 블록 것은 블록에 있다 */
	@JdbcTypeCode(SqlTypes.JSON)
	private List<Map<String, Object>> review;

	/** 검사 · 변호인 분량이 치우쳤다는 경고. 자동으로 고치지 않고 관리자가 보고 판단한다(BE-107) */
	private String balanceWarning;

	/**
	 * 파이프라인이 공개해도 된다고 본 결과인지. 생성 실패 · 호출 한도 중단 · 만들지 못한 단위 · 검수 오류가 있으면
	 * {@code false}다. 블록마다의 값({@link CaseScriptBlock#isPublishable()})과 함께 승인 조건이다({@link #isApprovable()})
	 */
	private boolean publishable;

	@CreationTimestamp
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

	public static CaseScript of(LegalCase legalCase, String model, Map<String, Object> castRoles, String issue,
			Boolean admits, List<String> pending, List<Map<String, Object>> review, String balanceWarning,
			boolean publishable, Instant generatedAt, boolean stopped) {
		CaseScript script = new CaseScript();
		script.legalCase = legalCase;
		script.model = model;
		script.generatedAt = generatedAt;
		script.stopped = stopped;
		// Map.copyOf는 null 값을 허용하지 않는다. 배역 한쪽이 비어 오면(파이프라인이 한 측만 채운 경우)
		// 적재 전체가 NPE로 막히므로 새 맵에 그대로 담는다
		script.castRoles = castRoles == null ? null : new LinkedHashMap<>(castRoles);
		script.issue = issue;
		script.admits = admits;
		script.pending = pending == null ? new ArrayList<>() : new ArrayList<>(pending);
		script.review = review == null ? new ArrayList<>() : new ArrayList<>(review);
		script.balanceWarning = balanceWarning;
		script.publishable = publishable;
		return script;
	}

	/** 블록을 붙인다. 같은 단위가 이미 있으면 거절한다(DB에도 uk_case_script_block_unit) */
	public CaseScriptBlock addBlock(ScriptUnit unit, Object content, int revision, Map<String, Object> basis,
			List<Map<String, Object>> review, List<Object> warnings, List<Map<String, Object>> repairs) {
		if (block(unit).isPresent()) {
			throw new IllegalStateException("이미 있는 블록이다: " + unit);
		}
		CaseScriptBlock block = CaseScriptBlock.of(this, unit, content, revision, basis, review, warnings, repairs);
		blocks.add(block);
		blocks.sort(Comparator.comparing(CaseScriptBlock::getUnit));
		return block;
	}

	public Optional<CaseScriptBlock> block(ScriptUnit unit) {
		return blocks.stream().filter(block -> block.getUnit() == unit).findFirst();
	}

	/** 증거조사 대사 (U3 블록). 없으면 빈 목록 */
	public List<Map<String, Object>> evidence() {
		return block(ScriptUnit.U3).map(CaseScriptBlock::lines).orElse(List.of());
	}

	/** 대본의 검수 상태. 모든 블록이 승인이면 승인, 하나라도 반려면 반려, 그 밖은 대기 */
	public ReviewStatus reviewState() {
		if (isPublic()) {
			return ReviewStatus.APPROVED;
		}
		if (blocks.stream().anyMatch(block -> block.getReviewStatus() == ReviewStatus.REJECTED)) {
			return ReviewStatus.REJECTED;
		}
		return ReviewStatus.PENDING;
	}

	/**
	 * 공개할 수 있는 대본인지 = <b>모든 블록이 승인됐는지</b> (BE-109 서빙 기준, API 1 · 16 · 18의 {@code scriptReady}).
	 * 사건 공개 여부와는 별개다. {@code CaseScriptRepository.findApprovedCaseIds}가 같은 조건을 쿼리로 쓴다
	 */
	public boolean isPublic() {
		return !blocks.isEmpty() && blocks.stream().allMatch(CaseScriptBlock::isApproved);
	}

	/** 승인할 수 있는지: 파이프라인이 공개해도 된다고 봤고, 모든 블록에 오류가 없다 */
	public boolean isApprovable() {
		return publishable && !blocks.isEmpty() && blocks.stream().allMatch(CaseScriptBlock::isPublishable);
	}

	/** 가장 최근에 검수한 블록 (검수 화면의 검수자 · 시각 · 사유). 통승인 · 통거절이라 1차는 모든 블록이 같다 */
	public Optional<CaseScriptBlock> lastReviewed() {
		return blocks.stream().filter(block -> block.getReviewedAt() != null)
			.max(Comparator.comparing(CaseScriptBlock::getReviewedAt));
	}

	/** 통승인 (A-13-11) — 모든 블록을 승인한다. 승인할 수 없는 대본이면 거절한다 */
	public void approve(Long adminId, Instant at) {
		if (!isApprovable()) {
			throw new IllegalStateException("오류가 있거나 만들지 못한 단위가 있는 대본은 승인할 수 없다 — 다시 생성해야 한다");
		}
		blocks.forEach(block -> block.approve(adminId, at));
	}

	/** 통거절 (A-13-12) — 모든 블록을 반려한다. 반려한 대본은 공개되지 않는다 */
	public void reject(Long adminId, Instant at, String note) {
		blocks.forEach(block -> block.reject(adminId, at, note));
	}

	/**
	 * 증거조사 대사 한 줄을 관리자가 직접 고친다 (A-13-13). 숫자 하나 · 말투 때문에 모델을 다시 부르는 것보다 싸다.
	 *
	 * <p>고치면 <b>모든 블록의 검수 기록을 지운다</b>(1차는 통승인이라 대본 전체가 다시 검수 대기). 내용이 바뀌었는데 지난
	 * 승인이 남아 있으면 검수하지 않은 대사가 공개된다. 블록 단위로 다시 검수하는 것은 2차다.
	 *
	 * @return 그 요소의 대사를 찾아 고쳤으면 true, 대본에 U3가 없거나 그 요소가 없으면 false
	 */
	public boolean editLine(int factorId, String text, Instant at) {
		Optional<CaseScriptBlock> evidence = block(ScriptUnit.U3);
		if (evidence.isEmpty() || !evidence.get().editLine(factorId, text, at)) {
			return false;
		}
		blocks.forEach(CaseScriptBlock::clearReview);
		this.publishable = recomputePublishable();
		return true;
	}

	/**
	 * 파이프라인과 <b>같은 조건</b>을 다시 적용한다 — 멈추지 않았고, 만들지 못한 단위가 없고, 대본 전체 검수 오류가 없고,
	 * 모든 블록에 오류가 없다(`script_assembler.build_script` · `pipeline.py` stage_script). 사람이 고쳐 빈 대사가 사라지면
	 * 승인할 수 있게 올라가고, 호출 한도로 멈춘 대본은 <b>줄 자체가 없어</b> 고쳐도 올라가지 않는다(그때는 다시 생성해야 한다).
	 */
	private boolean recomputePublishable() {
		return !stopped && pending.isEmpty() && review.stream().noneMatch(issue -> ERROR.equals(issue.get("severity")))
			&& !blocks.isEmpty() && blocks.stream().allMatch(CaseScriptBlock::isPublishable);
	}
}
