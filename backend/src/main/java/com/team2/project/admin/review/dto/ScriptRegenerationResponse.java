package com.team2.project.admin.review.dto;

import com.team2.project.legalcase.domain.CaseScriptRegeneration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 대본 블록 재생성 작업 (BE-129 2차, A-13-15 · 16 응답).
 *
 * @param status        QUEUED · RUNNING · SUCCEEDED · FAILED (사건 등록 A-15와 같은 값)
 * @param stages        파이프라인 단계 상태(재생성은 {@code script} 하나)
 * @param applicable    지금 반영(A-13-17)할 수 있는지 — 끝났고, 반영하지 않았고, 그 사건의 가장 최근 작업이고, 요청한 뒤 입력이
 *                      바뀌지 않았다
 * @param notApplicable 반영할 수 없는 이유 (반영할 수 있거나 아직 도는 중이면 null)
 * @param preview       끝난 작업의 결과 미리보기. 아직 끝나지 않았거나, 실패했거나, 이미 반영했거나, 결과를 가져오지 못했으면 null
 */
public record ScriptRegenerationResponse(Long regenerationId, String status, List<String> units, boolean cascade,
		List<Stage> stages, String errorCode, String message, OffsetDateTime createdAt, OffsetDateTime finishedAt,
		OffsetDateTime appliedAt, boolean applicable, String notApplicable, Preview preview) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public record Stage(String name, String status) {
	}

	/**
	 * 재생성 결과 미리보기.
	 *
	 * @param units               다시 만들기로 한 블록 (고른 블록 + 연동 표로 더한 블록)
	 * @param regenerated         실제로 만든 블록 — 반영하면 이 블록만 바뀐다
	 * @param pending             만들지 못한 블록 (지금 값이 그대로 남는다)
	 * @param stopped             호출 한도로 멈췄는지
	 * @param publishable         만든 블록에 검수 오류가 없는지
	 * @param blocks              만든 블록 (A-13-10 블록과 같은 모양, 검수 상태 칸 없음)
	 * @param meta                반영한 뒤의 메타 {@code {cast, issue, admits}}
	 * @param manualLinesReplaced U3를 다시 만들면 바뀌는, 관리자가 직접 고친 대사 수(A-13-13). 화면이 반영 전에 확인을 받는다
	 */
	public record Preview(List<String> units, List<String> regenerated, List<String> pending, boolean stopped,
			boolean publishable, List<Block> blocks, Map<String, Object> meta, long manualLinesReplaced) {
	}

	public record Block(String unit, Object content, int revision, Map<String, Object> basis,
			List<Map<String, Object>> review, List<Object> warnings, List<Map<String, Object>> repairs) {
	}

	public static ScriptRegenerationResponse of(CaseScriptRegeneration entity, boolean applicable, String notApplicable,
			Preview preview) {
		List<Stage> stages = entity.getStages() == null ? List.of()
			: entity.getStages().stream().map(stage -> new Stage(stage.get("name"), stage.get("status"))).toList();
		return new ScriptRegenerationResponse(entity.getId(),
			entity.getStatus() == null ? "QUEUED" : entity.getStatus().name(), entity.getUnits(), entity.isCascade(),
			stages, entity.getErrorCode(), entity.getMessage(), kst(entity.getCreatedAt()), kst(entity.getFinishedAt()),
			kst(entity.getAppliedAt()), applicable, notApplicable, preview);
	}

	private static OffsetDateTime kst(Instant instant) {
		return instant == null ? null : instant.atZone(KST).toOffsetDateTime();
	}
}
