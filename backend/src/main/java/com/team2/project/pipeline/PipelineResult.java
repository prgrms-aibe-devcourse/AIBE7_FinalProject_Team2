package com.team2.project.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;
import java.util.List;

/**
 * 파이프라인이 만든 결과 (COMMON-50 `GET /jobs/{id}/result`). 적재(BE-93 2차)의 입력이다.
 *
 * <p><b>판결문 원문은 들어 있지 않다</b> (요구사항 FR-5-3). 적재할 때 쓰는 원문은 백엔드가 A-14에서 받아
 * `case_import_source`에 들고 있던 값이다.
 *
 * <p>안쪽 내용을 {@link JsonNode}로 두는 이유: 사건 입력 JSON은 섹션 · 판단 요소 · 형벌 규칙 · 참고 자료가
 * 겹겹이 들어간 큰 구조이고, 그 형식을 정하는 쪽은 파이프라인(`tools/ai-judgment`)이다. 레코드로 전부 받아
 * 적으면 파이프라인이 필드를 하나 늘릴 때마다 백엔드가 같이 깨진다 — 적재에 실제로 쓰는 값만 꺼내 쓰고,
 * 모르는 필드는 그냥 지나가게 둔다.
 *
 * @param caseContent   사건 입력 (`case.json`)
 * @param report        extract 검증 보고 (`report.json`). 요약어 · 가치관 축 같은 보조 값이 들어 있다
 * @param courtDraft    재판부 판결 (BE-14 형식, court 단계의 `courtDraft`). extract의 court 산출물은
 *                      형벌 값만 담은 사전 학습 점검 전용 파일이라 적재에 쓸 수 없다
 * @param judgment      회차 선택으로 고른 AI 판결
 * @param selection     어느 모델 · 몇 번째 회차를 왜 골랐는지 (ai_generation 기록용)
 * @param axis          판단 요소 가치관 축 투표 결과
 * @param script        사건 대본 — 증거조사 대사 (BE-107 script 단계). 대본을 돌리지 않은 작업은 null이다.
 *                      {@code evidence}(대사 배열) · {@code publishable} · {@code model} · {@code cast} ·
 *                      {@code balanceWarning}이 들어 있다. 적재는 BE-108이 한다
 * @param classify      사건 분류 (BE-123 classify 단계). 분류를 돌리지 않은 작업은 null이다. 적재는
 *                      {@code classification.sensitiveFlags}만 읽어 legal_case.sensitive_flags에 넣는다(BE-125)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PipelineResult(
	String jobId,
	@com.fasterxml.jackson.annotation.JsonProperty("case") JsonNode caseContent,
	JsonNode report,
	JsonNode courtDraft,
	JsonNode judgment,
	Selection selection,
	JsonNode axis,
	JsonNode script,
	JsonNode classify
) {

	/** @param warnings 검증 경고. 적재를 막지는 않고 ai_generation에 남겨 관리자가 보게 한다 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Selection(String modelSpec, Integer runIndex, String strategy, String reason,
		List<String> warnings) {
	}

	/** 적재할 수 있는 결과인지. 사건과 AI 판결이 모두 있어야 사건 한 건이 만들어진다 */
	public boolean loadable() {
		return caseContent != null && !caseContent.isNull() && judgment != null && !judgment.isNull();
	}
}
