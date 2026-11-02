package com.team2.project.admin.review.dto;

import com.team2.project.judgment.domain.ReviewStatus;
import com.team2.project.legalcase.domain.BlockStaleness;
import com.team2.project.legalcase.domain.CaseScript;
import com.team2.project.legalcase.domain.CaseScriptBlock;
import com.team2.project.legalcase.domain.ScriptUnit;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 사건 대본 검수 응답 (BE-108, A-13-10. 블록은 BE-126).
 *
 * <p>검수 칸(reviewStatus · isPublished · reviewedBy · reviewedAt · reviewNote)은 <b>판결 후보 A-8과 같은
 * 이름 · 같은 형식</b>이다 — 화면이 검수 영역을 같은 부품으로 그릴 수 있게 한다(FE 요청, 2026-10-27). 검수 상태는 블록에
 * 있고 여기 값은 블록에서 계산한다: 다섯 단위가 모두 있고 모두 승인이면 {@code APPROVED}, 하나라도 반려면 {@code REJECTED}, 그 밖은
 * {@code PENDING}. 검수자 · 시각 · 사유는 가장 최근에 검수한 블록의 것이다(1차는 통승인 · 통거절이라 모든 블록이 같다).
 *
 * <p>{@code evidence[]}는 U3 블록의 대사이고 BE-108 때와 같은 모양이다(화면이 쓰는 값: {@code factorId} · {@code role} ·
 * {@code character} · {@code text} · {@code validation.warnings} · {@code attempts}). 여기에 <b>{@code label}을 더해 준다</b> —
 * 어느 사실에 대한 대사인지는 사건의 판단 요소에 있다(파이프라인 {@code factorId} = {@code factor.display_order}).
 * 생성에 실패한 줄은 {@code text}가 {@code null}이다.
 *
 * <p>{@code publishable}이 {@code false}면 승인할 수 없다 — 끝내 생성하지 못한 줄 · 빠진 단위가 있거나({@code pending},
 * 증거조사만 만든 대본 포함),
 * 호출 한도로 멈췄거나({@code stopped}), 검수 조건 오류가 있다(블록의 {@code review}, 대본 전체는 {@code review}).
 *
 * @param reviewStatus 검수 상태. 블록에서 계산한다 (위)
 * @param isPublished  지금 공개되는 대본인지(= 다섯 단위 모두 승인, 증거조사만 있는 대본은 승인돼도 false). <b>사건 공개 여부와는 별개다</b>
 * @param reviewedBy   검수한 관리자 이메일. A-8과 같은 값을 쓴다(계정이 지워졌으면 null)
 * @param generatedAt  파이프라인이 만든 시각. 적재 시각({@code updatedAt})보다 앞선다
 * @param meta         {@code {cast, issue, admits}} — 프론트 공통 틀이 고정 문구를 고르는 값 (API 18과 같다)
 * @param pending      파이프라인이 만들지 못한 단위
 * @param review       대본 전체에 걸린 검수 결과({@code REVIEW_FAILED}). 블록 것은 블록에 있다
 * @param blocks       단위 블록 (U0 → U4 순서, 만든 단위만)
 */
public record CaseScriptResponse(List<Map<String, Object>> evidence, String model, Map<String, Object> cast,
		String balanceWarning, boolean publishable, boolean stopped, ReviewStatus reviewStatus, boolean isPublished,
		String reviewedBy, OffsetDateTime reviewedAt, String reviewNote, OffsetDateTime generatedAt,
		OffsetDateTime updatedAt, Map<String, Object> meta, List<String> pending, List<Map<String, Object>> review,
		List<Block> blocks) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/**
	 * 블록 하나. {@code review} · {@code warnings} · {@code repairs}는 관리자 화면 전용이다(공개 API에는 싣지 않는다).
	 *
	 * @param content     블록 값. 파이프라인이 만든 모양 그대로다(검수 결과 {@code path}가 가리킨다)
	 * @param revision    판. 값이 바뀔 때마다 오른다
	 * @param basis       만들 때 본 값(앞 블록 판 · 분류 · 배역 · 사건 내용 판 · 기사 판)
	 * @param publishable 오류 없이 승인할 수 있는 블록인지
	 * @param stale       (BE-126 2차) 만들 때 본 값이 지금 값과 다른지 — 낡은 블록. 표시가 기본이다
	 * @param staleReasons 바뀐 근거 키 (예: {@code U0} · {@code cast} · {@code paper})
	 * @param staleBlocksApproval 승인을 막는 낡음인지 (사건 내용 · 기사 · 민감 플래그). 재생성하거나 "확인했음"(A-13-20)으로 푼다
	 * @param unknownBasis 근거에 없어 비교하지 못한 키 (이관 · 증거조사만 만든 대본) — 낡지 않은 것으로 보되 화면이 표시한다
	 */
	public record Block(String unit, Object content, int revision, Map<String, Object> basis,
			List<Map<String, Object>> review, List<Object> warnings, List<Map<String, Object>> repairs,
			boolean publishable, ReviewStatus reviewStatus, String reviewedBy, OffsetDateTime reviewedAt,
			String reviewNote, boolean stale, List<String> staleReasons, boolean staleBlocksApproval,
			List<String> unknownBasis) {

		static Block of(CaseScriptBlock block, Function<Long, String> emailOf, BlockStaleness staleness) {
			BlockStaleness value = staleness == null ? new BlockStaleness(List.of(), List.of()) : staleness;
			return new Block(block.getUnit().name(), block.getContent(), block.getRevision(), block.getBasis(),
				block.getReview(), block.getWarnings(), block.getRepairs(), block.isPublishable(), block.reviewState(),
				emailOf.apply(block.getReviewedByAdminId()), kst(block.getReviewedAt()), block.getReviewNote(),
				value.stale(), value.reasons(), value.blocksApproval(), value.unknown());
		}
	}

	/**
	 * @param labelsByFactorId 판단 요소 문구. 키는 파이프라인 {@code factorId}(= {@code factor.display_order})다.
	 *                         요소가 지워졌거나 못 찾으면 그 줄의 {@code label}은 비운다
	 * @param emailOf          검수자 이메일. 부르는 쪽이 관리자 계정에서 찾는다(A-8과 같은 방식, id가 null이면 null)
	 */
	public static CaseScriptResponse of(CaseScript script, Map<Integer, String> labelsByFactorId,
			Function<Long, String> emailOf, Map<ScriptUnit, BlockStaleness> staleness) {
		CaseScriptBlock last = script.lastReviewed().orElse(null);
		return new CaseScriptResponse(withLabels(script.evidence(), labelsByFactorId), script.getModel(),
			script.getCastRoles(), script.getBalanceWarning(), script.isApprovable(), script.isStopped(),
			script.reviewState(), script.isPublic(), last == null ? null : emailOf.apply(last.getReviewedByAdminId()),
			last == null ? null : kst(last.getReviewedAt()), last == null ? null : last.getReviewNote(),
			kst(script.getGeneratedAt()), kst(updatedAt(script)), meta(script), script.getPending(), script.getReview(),
			script.getBlocks().stream().map(block -> Block.of(block, emailOf, staleness.get(block.getUnit()))).toList());
	}

	/** API 18의 meta와 같은 모양. Map.of는 null 값을 허용하지 않아(issue · admits가 비어 있을 수 있다) 새 맵에 담는다 */
	private static Map<String, Object> meta(CaseScript script) {
		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("cast", script.getCastRoles());
		meta.put("issue", script.getIssue());
		meta.put("admits", script.getAdmits());
		return meta;
	}

	/** 대본이나 블록 중 마지막으로 바뀐 시각 (대사를 고치면 블록만 바뀐다) */
	private static Instant updatedAt(CaseScript script) {
		return Stream.concat(Stream.of(script.getUpdatedAt()), script.getBlocks().stream().map(CaseScriptBlock::getUpdatedAt))
			.filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
	}

	private static List<Map<String, Object>> withLabels(List<Map<String, Object>> evidence,
			Map<Integer, String> labelsByFactorId) {
		// Map.copyOf · Map.of는 null 값을 허용하지 않는다 — 생성에 실패한 줄은 text가 null이고 label도 null일 수 있어
		// 그대로 쓰면 NPE가 난다. 새로 만든 맵을 그대로 돌려준다(원본은 건드리지 않는다)
		return evidence.stream().map(line -> {
			Map<String, Object> copy = new LinkedHashMap<String, Object>(line);
			copy.put("label", label(line.get("factorId"), labelsByFactorId));
			return (Map<String, Object>) copy;
		}).toList();
	}

	/** 저장된 JSON의 숫자는 Integer · Long 어느 쪽으로도 올 수 있어 Number로 받아 맞춘다 */
	private static String label(Object factorId, Map<Integer, String> labelsByFactorId) {
		if (!(factorId instanceof Number number) || labelsByFactorId == null) {
			return null;
		}
		return labelsByFactorId.get(number.intValue());
	}

	/** 시각은 A-8 · A-6과 같이 KST 오프셋으로 내려준다 */
	private static OffsetDateTime kst(Instant instant) {
		return instant == null ? null : instant.atZone(KST).toOffsetDateTime();
	}
}
