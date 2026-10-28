package com.team2.project.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * 파이프라인 서비스에 보내는 작업 생성 요청 (COMMON-50 `POST /jobs`).
 *
 * <p><b>판결문 원문만 보낸다.</b> 사건번호 · 법원 · 선고일 같은 원본 정보는 파이프라인이 쓰지 않고
 * 적재할 때 백엔드가 쓰므로 보내지 않는다(case_import_source에 둔다).
 *
 * @param sources          심급별 판결문. 요청에 들어온 순서를 그대로 쓴다
 * @param models           AI 판결 생성에 쓸 모델 (서비스가 필수로 받는다)
 * @param stages           단계 설정 덮어쓰기. extract 모델을 지정할 때만 쓴다. load는 서비스가 어떤 값이 와도 끈다
 * @param finalSourceIndex 최종 확정 판결의 번호(0부터). 없으면 파이프라인이 심급으로 정한다
 * @param until            어느 단계까지 돌릴지. 비우면 서비스 기본값(axis)이라 대본은 돌지 않는다 —
 *                         대본까지 만들려면 {@code script}를 보낸다(BE-108)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PipelineJobRequest(
	List<Source> sources,
	List<String> models,
	Map<String, Object> stages,
	Integer finalSourceIndex,
	String until
) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Source(String originalText, String note) {
	}
}
