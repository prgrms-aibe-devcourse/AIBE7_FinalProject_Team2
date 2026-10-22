package com.team2.project.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 파이프라인 서비스가 돌려주는 작업 상태 (COMMON-50 `GET /jobs/{id}`).
 *
 * <p>API 명세 A-15와 값이 같도록 서비스 쪽에서 맞춰 두었다. 백엔드는 이 값을 거의 그대로 옮겨 내려준다.
 *
 * @param status QUEUED · RUNNING · SUCCEEDED · FAILED
 * @param error  실패 사유 한 줄. <b>원문 · 모델 출력이 들어가지 않는다</b> (서비스에서 첫 줄만 보낸다)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PipelineJob(
	String jobId,
	String status,
	List<Stage> stages,
	String error,
	String createdAt,
	String startedAt,
	String finishedAt
) {

	/**
	 * 단계별 진행 상황.
	 *
	 * @param name   extract · court · contamination · rag · generate · select · axis · load
	 * @param status PENDING · RUNNING · DONE · SKIPPED · FAILED
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Stage(String name, String status, String error) {
	}
}
