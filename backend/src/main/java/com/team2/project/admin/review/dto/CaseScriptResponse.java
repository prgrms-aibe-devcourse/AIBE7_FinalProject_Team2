package com.team2.project.admin.review.dto;

import com.team2.project.judgment.domain.ReviewStatus;
import com.team2.project.legalcase.domain.CaseScript;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 사건 대본 검수 응답 (BE-108, A-13-10). 지금 담는 것은 <b>증거조사 대사뿐</b>이다 — 1막 해설 · 피고인신문 등은
 * 파이프라인 범위 밖이다(COMMON-65 · FE-33).
 *
 * <p>검수 칸(reviewStatus · isPublished · reviewedBy · reviewedAt · reviewNote)은 <b>판결 후보 A-8과 같은
 * 이름 · 같은 형식</b>이다 — 화면이 검수 영역을 같은 부품으로 그릴 수 있게 한다(FE 요청, 2026-10-27).
 *
 * <p>대사 한 줄({@code evidence[]})은 파이프라인이 만든 모양 그대로이고(화면이 쓰는 값: {@code factorId} ·
 * {@code role} · {@code character} · {@code text} · {@code validation.warnings} · {@code attempts}),
 * 여기에 <b>{@code label}을 더해 준다</b> — 어느 사실에 대한 대사인지는 사건의 판단 요소에 있고
 * (파이프라인 {@code factorId} = {@code factor.display_order}), 화면이 그 연결을 다시 하지 않아도 되게 한다.
 * 생성에 실패한 줄은 {@code text}가 {@code null}이다.
 *
 * <p>{@code publishable}이 {@code false}면 승인할 수 없다. 이유는 둘이다 — 끝내 생성하지 못한 줄이 있거나
 * ({@code text: null}), 호출 한도로 중간에 멈췄거나({@code stopped}). 화면이 구분해 안내할 수 있게 둘 다 준다.
 *
 * @param reviewStatus 검수 상태. 기록이 없으면 {@code PENDING} (A-8과 같다)
 * @param isPublished  지금 공개되는 대본인지(= 승인됨). <b>사건 공개 여부와는 별개다</b>
 * @param reviewedBy   검수한 관리자 이메일. A-8과 같은 값을 쓴다(계정이 지워졌으면 null)
 * @param generatedAt  파이프라인이 만든 시각. 적재 시각({@code updatedAt})보다 앞선다
 */
public record CaseScriptResponse(List<Map<String, Object>> evidence, String model, Map<String, Object> cast,
		String balanceWarning, boolean publishable, boolean stopped, ReviewStatus reviewStatus, boolean isPublished,
		String reviewedBy, OffsetDateTime reviewedAt, String reviewNote, OffsetDateTime generatedAt,
		OffsetDateTime updatedAt) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/**
	 * @param labelsByFactorId 판단 요소 문구. 키는 파이프라인 {@code factorId}(= {@code factor.display_order})다.
	 *                         요소가 지워졌거나 못 찾으면 그 줄의 {@code label}은 비운다
	 * @param reviewedBy       검수자 이메일. 부르는 쪽이 관리자 계정에서 찾아 넘긴다(A-8과 같은 방식)
	 */
	public static CaseScriptResponse of(CaseScript script, Map<Integer, String> labelsByFactorId, String reviewedBy) {
		return new CaseScriptResponse(withLabels(script.getEvidence(), labelsByFactorId), script.getModel(),
			script.getCastRoles(), script.getBalanceWarning(), script.isPublishable(), script.isStopped(),
			script.reviewState(), script.isPublic(), reviewedBy, kst(script.getReviewedAt()),
			script.getReviewNote(), kst(script.getGeneratedAt()), kst(script.getUpdatedAt()));
	}

	private static List<Map<String, Object>> withLabels(List<Map<String, Object>> evidence,
			Map<Integer, String> labelsByFactorId) {
		if (evidence == null) {
			return List.of();
		}
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
