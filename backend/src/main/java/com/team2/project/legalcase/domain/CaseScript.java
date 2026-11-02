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
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * <p>대본 전체(BE-121)는 만든 단위만큼 블록이 있고, 만들지 못한 단위는 {@link #pending}에 남는다. 증거조사 대사만 만든
 * 대본(BE-107, {@code stages.script.full = false})은 U3 블록 하나이고 나머지 단위가 {@code pending}이다 — <b>체험할 수 없는
 * "준비 중" 대본</b>이다(해송님 합의, BE-126 PR #367 리뷰 1번).
 *
 * <p><b>검수 상태는 블록에 있다.</b> 대본의 상태는 블록에서 계산한다 — 다섯 단위가 모두 있고 모두 승인됐으면 공개({@link #isPublic()}).
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

	/**
	 * 재생성한 블록 하나 (BE-129 2차). 파이프라인 결과의 블록 모양 그대로이고, {@code basis}의 사건 내용 · 기사 판은 반영하는 쪽이
	 * 요청 당시 판으로 채운 값이다
	 */
	public record RegeneratedBlock(Object content, int revision, Map<String, Object> basis,
			List<Map<String, Object>> review, List<Object> warnings, List<Map<String, Object>> repairs) {
	}

	/**
	 * 재생성 결과를 반영한다 (BE-129 2차, A-13-17). 다시 만든 블록만 바꾸거나 새로 붙이고 검수 기록을 지운다 — 다른 블록(승인했을 수
	 * 있다)은 건드리지 않는다. 메타(배역 · 쟁점 · 혐의 인정)는 지금 값으로 맞추고, 만들지 못했던 단위가 채워졌으면 {@code pending}에서
	 * 뺀다. 대본 전체 검수 결과({@code REVIEW_FAILED})는 재생성 때 대본 전체를 다시 검사한 결과로 바꾼다.
	 *
	 * @param stopped 재생성이 호출 한도로 멈췄는지. 대본의 {@code stopped}는 마지막 생성의 값이다
	 */
	public void applyRegeneration(Map<ScriptUnit, RegeneratedBlock> regenerated, Map<String, Object> cast, String issue,
			List<Map<String, Object>> scriptReview, boolean stopped, String model) {
		this.pending = new ArrayList<>(pending);	// DB에서 읽은 목록이 바꿀 수 없는 목록일 수 있다
		for (Map.Entry<ScriptUnit, RegeneratedBlock> entry : regenerated.entrySet()) {
			RegeneratedBlock value = entry.getValue();
			Optional<CaseScriptBlock> existing = block(entry.getKey());
			if (existing.isPresent()) {
				existing.get().replace(value.content(), value.revision(), value.basis(), value.review(), value.warnings(),
					value.repairs());
			} else {
				addBlock(entry.getKey(), value.content(), value.revision(), value.basis(), value.review(), value.warnings(),
					value.repairs());
			}
			pending.remove(entry.getKey().name());
		}
		// 결과에 메타가 비어 오면 지금 값을 둔다(PR #381 리뷰 4번)
		if (cast != null) {
			this.castRoles = new LinkedHashMap<>(cast);
		}
		if (issue != null) {
			this.issue = issue;
		}
		this.admits = block(ScriptUnit.U0).map(CaseScript::admitsOf).orElse(null);
		this.review = scriptReview == null ? new ArrayList<>() : new ArrayList<>(scriptReview);
		this.stopped = stopped;
		if (model != null) {
			this.model = model;
		}
		this.publishable = recomputePublishable();
	}

	/** U0 세 번째 답으로 혐의 인정 여부를 정한다 (적재 {@code CaseScriptLoader#admits}와 같은 규칙) */
	private static Boolean admitsOf(CaseScriptBlock design) {
		if (!(design.getContent() instanceof Map<?, ?> content) || !(content.get("questions") instanceof List<?> questions)
			|| questions.size() <= 2 || !(questions.get(2) instanceof Map<?, ?> third)) {
			return null;
		}
		return ScriptAnswers.admits(third.get("a") instanceof String answer ? answer : null);
	}

	/** 증거조사 대사 (U3 블록). 없으면 빈 목록 */
	public List<Map<String, Object>> evidence() {
		return block(ScriptUnit.U3).map(CaseScriptBlock::lines).orElse(List.of());
	}

	/** 대본의 검수 상태. 공개 대본(다섯 단위 모두 승인)이면 승인, 하나라도 반려면 반려, 그 밖은 대기 */
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
	 * 공개할 수 있는 대본인지 = <b>다섯 단위(U0 ~ U4)가 모두 있고 모두 승인됐는지</b> (BE-109 서빙 기준, API 1 · 16 · 18의
	 * {@code scriptReady}). 증거조사만 있는 대본은 승인돼 있어도 공개하지 않는다(준비 중). 사건 공개 여부와는 별개다.
	 * {@code CaseScriptRepository.findApprovedCaseIds}가 같은 조건을 쿼리로 쓴다
	 */
	public boolean isPublic() {
		return isComplete() && blocks.stream().allMatch(CaseScriptBlock::isApproved);
	}

	/**
	 * 승인할 수 있는지: 다섯 단위가 모두 있고, 만들지 못한 것({@code pending})이 없고, 파이프라인이 공개해도 된다고 봤고,
	 * 모든 블록에 오류가 없다. {@code pending}에는 단위가 아닌 {@code "PAPER"}(기사)도 들어와 블록이 다섯 개여도 따로 본다
	 * (PR #367 리뷰)
	 */
	public boolean isApprovable() {
		return publishable && pending.isEmpty() && isComplete()
			&& blocks.stream().allMatch(CaseScriptBlock::isPublishable);
	}

	/** 다섯 단위가 모두 있는지. 만들지 못한 단위가 있거나 증거조사만 만든 대본이면 false */
	public boolean isComplete() {
		return Arrays.stream(ScriptUnit.values()).allMatch(unit -> block(unit).isPresent());
	}

	/**
	 * 블록마다 낡음 (BE-126 2차). 블록을 만들 때 본 값({@code basis})과 지금 값({@link #currentBasis})을 비교한다 — 앞 블록 판 · 사건
	 * 내용 판 · 기사 판 · 배역 · 분류 네 축 · 민감 플래그. 근거에 키가 없으면 비교하지 않고 "모름"으로 둔다(이관 · 증거조사만 만든
	 * 대본, 11/01 합의). 지금 값이 없으면(미분류 사건의 분류 · 민감 플래그) 그 키도 모름이다.
	 *
	 * @param paperRevision 기사의 지금 판 ({@link CasePaper#getRevision()}), 기사가 없으면 null
	 */
	public Map<ScriptUnit, BlockStaleness> staleness(LegalCase legalCase, Integer paperRevision) {
		Map<String, Object> current = currentBasis(legalCase, paperRevision);
		Map<ScriptUnit, BlockStaleness> result = new LinkedHashMap<>();
		for (CaseScriptBlock block : blocks) {
			List<String> reasons = new ArrayList<>();
			List<String> unknown = new ArrayList<>();
			for (String key : block.getUnit().basis()) {
				boolean comparable = block.getBasis().containsKey(key)
					&& (current.get(key) != null || ScriptUnit.PAPER_BASIS.equals(key));
				if (!comparable) {
					unknown.add(key);
				} else if (!Objects.equals(normalize(block.getBasis().get(key)), normalize(current.get(key)))) {
					reasons.add(key);
				}
			}
			result.put(block.getUnit(), new BlockStaleness(List.copyOf(reasons), List.copyOf(unknown)));
		}
		return result;
	}

	/**
	 * 지금 값 — 블록 근거와 같은 키 · 같은 모양이다. 앞 블록은 지금 판(블록이 없으면 null), 사건 내용 · 기사는 판, 배역 · 분류 · 민감
	 * 플래그는 사건의 값(민감 플래그는 파이프라인 {@code build_blocks}처럼 정렬)이다
	 */
	public Map<String, Object> currentBasis(LegalCase legalCase, Integer paperRevision) {
		Map<String, Object> current = new LinkedHashMap<>();
		for (ScriptUnit unit : ScriptUnit.values()) {
			current.put(unit.name(), block(unit).map(CaseScriptBlock::getRevision).orElse(null));
		}
		current.put(ScriptUnit.CASE_BASIS, legalCase.getContentRevision());
		current.put(ScriptUnit.PAPER_BASIS, paperRevision);
		current.put("cast", legalCase.getCastRoles() == null ? null : new LinkedHashMap<>(legalCase.getCastRoles()));
		Map<String, Object> classification = legalCase.getClassification();
		for (String axis : ScriptClassification.AXES) {
			current.put(axis, classification == null ? null : classification.get(axis));
		}
		current.put("sensitiveFlags", legalCase.getSensitiveFlags() == null ? null
			: legalCase.getSensitiveFlags().stream().sorted().toList());
		return current;
	}

	/** 비교용 모양: JSON 숫자는 Integer · Long 어느 쪽으로도 와서 long으로, 민감 플래그 같은 목록은 정렬한다 */
	private static Object normalize(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		if (value instanceof List<?> list) {
			return list.stream().map(String::valueOf).sorted().toList();
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> normalized = new java.util.TreeMap<>();
			map.forEach((key, item) -> normalized.put(String.valueOf(key), normalize(item)));
			return normalized;
		}
		return value;
	}

	/**
	 * 승인을 막는 낡음이 있는 블록 — 사건 내용 · 기사 · 민감 플래그가 바뀌었다({@link BlockStaleness#BLOCKING}). 적재 뒤 관리자가
	 * 개요를 고치거나(A-11) 기사를 다시 만들면(A-13-6) 그 값을 보고 만든 블록은 옛 내용을 말하고(1차), 민감 플래그를 고치면 가드 없이
	 * 만든 블록일 수 있다(2차). 재생성하거나 "확인했음"({@link #acknowledgeStale})으로 푼다.
	 */
	public List<ScriptUnit> blockingStale(LegalCase legalCase, Integer paperRevision) {
		return staleness(legalCase, paperRevision).entrySet().stream()
			.filter(entry -> entry.getValue().blocksApproval()).map(Map.Entry::getKey).toList();
	}

	/**
	 * 승인을 막는 낡음이 생긴 블록의 승인을 지운다 — 사건 내용 · 민감 플래그(A-11) · 기사(A-13-6 · 8)를 고친 뒤 부른다. 승인은
	 * 검수한 그때의 내용에 대한 것이라, 남겨 두면 승인한 뒤 고친 사건에서 옛 내용을 말하는 대본이 공개된다(PR #367 리뷰).
	 * 반려한 블록은 그대로 둔다.
	 *
	 * @return 승인을 지운 단위
	 */
	public List<ScriptUnit> revokeStaleApprovals(LegalCase legalCase, Integer paperRevision) {
		List<ScriptUnit> stale = blockingStale(legalCase, paperRevision);
		List<CaseScriptBlock> revoked = blocks.stream()
			.filter(block -> block.isApproved() && stale.contains(block.getUnit())).toList();
		revoked.forEach(CaseScriptBlock::clearReview);
		return revoked.stream().map(CaseScriptBlock::getUnit).toList();
	}

	/**
	 * "확인했음" (BE-126 2차) — 낡았지만 다시 만들지 않고 그대로 두기로 한 블록의 근거를 지금 값으로 맞춘다. 내용 · 판 · 검수 상태는
	 * 그대로다. 승인을 막던 낡음도 이것으로 풀린다(사소한 개요 수정의 출구, 11/01 합의)
	 *
	 * @return 확인한 낡음 키 (낡지 않았으면 빈 목록 — 근거는 그대로 둔다)
	 */
	public List<String> acknowledgeStale(ScriptUnit unit, LegalCase legalCase, Integer paperRevision) {
		CaseScriptBlock block = block(unit).orElseThrow(() -> new IllegalArgumentException("블록이 없다: " + unit));
		List<String> reasons = staleness(legalCase, paperRevision).get(unit).reasons();
		if (!reasons.isEmpty()) {
			block.acknowledge(currentBasis(legalCase, paperRevision));
		}
		return reasons;
	}

	/**
	 * 블록을 만든 뒤 <b>새로 생긴</b> 민감 플래그 (BE-126 2차, PR #383 리뷰). 그 플래그의 가드 문구 없이 만든 블록이라 "확인했음"으로
	 * 풀 수 없고 다시 만들어야 한다. 빠진 플래그는 확인으로 충분하다. 근거에 플래그가 없거나(모름) 지금 값이 없으면 빈 목록
	 */
	public List<String> addedSensitiveFlags(ScriptUnit unit, LegalCase legalCase) {
		CaseScriptBlock block = block(unit).orElseThrow(() -> new IllegalArgumentException("블록이 없다: " + unit));
		if (!(block.getBasis().get("sensitiveFlags") instanceof List<?> saved) || legalCase.getSensitiveFlags() == null) {
			return List.of();
		}
		List<String> before = saved.stream().map(String::valueOf).toList();
		return legalCase.getSensitiveFlags().stream().filter(flag -> !before.contains(flag)).sorted().toList();
	}

	/**
	 * 블록 하나를 승인한다 (BE-126 2차, 블록 단위). 마지막 블록 승인으로 대본이 공개되므로 통승인({@link #isApprovable()})과 같은
	 * 대본 조건을 지킨다(PR #383 CodeRabbit 리뷰) — {@link #canApproveBlocks()}와 그 블록에 오류가 없어야 한다. 승인을 막는 낡음은
	 * 부르는 쪽이 본다
	 */
	public void approveBlock(ScriptUnit unit, Long adminId, Instant at) {
		CaseScriptBlock block = block(unit).orElseThrow(() -> new IllegalArgumentException("블록이 없다: " + unit));
		if (!block.isPublishable() || !canApproveBlocks()) {
			throw new IllegalStateException("오류가 있는 블록 · 대본은 승인할 수 없다: " + unit);
		}
		block.approve(adminId, at);
	}

	/**
	 * 블록을 승인할 수 있는 대본인지 — 통승인 조건 중 대본 전체에 걸린 것: 호출 한도로 멈추지 않았고, 만들지 못한 것({@code pending},
	 * 기사 {@code "PAPER"} 포함)이 없고, 대본 전체 검수 오류(REVIEW_FAILED)가 없다. 대본의 {@code publishable}을 그대로 쓰지 않는
	 * 이유: 그 값은 블록마다의 오류도 합친 값이라, 다른 블록 하나에 오류가 있으면 멀쩡한 블록도 승인할 수 없게 된다. 블록 오류는
	 * 블록마다 보고, 마지막 블록까지 모두 승인됐다면 모든 블록에 오류가 없다는 뜻이라 공개 조건은 통승인과 같다
	 */
	public boolean canApproveBlocks() {
		return !stopped && pending.isEmpty() && review.stream().noneMatch(issue -> ERROR.equals(issue.get("severity")));
	}

	/** 블록 하나를 반려한다 (BE-126 2차). 반려한 블록이 있으면 대본은 공개되지 않는다 */
	public void rejectBlock(ScriptUnit unit, Long adminId, Instant at, String note) {
		block(unit).orElseThrow(() -> new IllegalArgumentException("블록이 없다: " + unit)).reject(adminId, at, note);
	}

	/**
	 * U0 · U1 · U2 · U4 블록의 문자열 한 줄을 고친다 (BE-126 2차, U3는 {@link #editLine}). 그 블록만 다시 검수 대기가 되고 판이
	 * 오른다 — 뒤 블록은 낡음으로 보인다. U0를 고치면 혐의 인정 여부({@code admits})를 다시 계산한다(10/30 결정).
	 *
	 * @return 그 블록 · 자리에 문자열이 있어 고쳤으면 true
	 */
	public boolean editText(ScriptUnit unit, String path, String text) {
		Optional<CaseScriptBlock> target = block(unit);
		if (target.isEmpty() || !target.get().editText(path, text)) {
			return false;
		}
		if (unit == ScriptUnit.U0) {
			this.admits = admitsOf(target.get());
		}
		this.publishable = recomputePublishable();
		return true;
	}

	/** 가장 최근에 검수한 블록 (검수 화면의 검수자 · 시각 · 사유). 통승인 · 통거절이라 1차는 모든 블록이 같다 */
	public Optional<CaseScriptBlock> lastReviewed() {
		return blocks.stream().filter(block -> block.getReviewedAt() != null)
			.max(Comparator.comparing(CaseScriptBlock::getReviewedAt));
	}

	/** 통승인 (A-13-11) — 모든 블록을 승인한다. 승인할 수 없는 대본이면 거절한다 */
	public void approve(Long adminId, Instant at) {
		if (!isApprovable()) {
			throw new IllegalStateException("오류가 있거나 빠진 단위가 있는 대본은 승인할 수 없다 — 다시 생성해야 한다");
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
	 * <p>고치면 <b>U3 블록의 검수 기록을 지운다</b>(2차 — 블록 단위로 다시 검수한다. 1차는 통승인이라 모든 블록을 지웠다). 내용이
	 * 바뀌었는데 지난 승인이 남아 있으면 검수하지 않은 대사가 공개된다. U3를 보고 만드는 뒤 블록은 없다.
	 *
	 * @return 그 요소의 대사를 찾아 고쳤으면 true, 대본에 U3가 없거나 그 요소가 없으면 false
	 */
	public boolean editLine(int factorId, String text, Instant at) {
		Optional<CaseScriptBlock> evidence = block(ScriptUnit.U3);
		if (evidence.isEmpty() || !evidence.get().editLine(factorId, text, at)) {
			return false;
		}
		evidence.get().clearReview();
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
