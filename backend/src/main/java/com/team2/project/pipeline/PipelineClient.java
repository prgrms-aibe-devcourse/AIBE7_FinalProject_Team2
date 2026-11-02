package com.team2.project.pipeline;

import tools.jackson.databind.JsonNode;

/**
 * AI 판결 파이프라인 서비스 호출 (BE-93 ↔ COMMON-50).
 *
 * <p>구현은 같은 서버의 내부 컨테이너에 HTTP로 붙는다. 인터페이스로 둔 이유는 테스트에서 바꿔 끼우기
 * 위해서이고, 외부 공급자를 갈아끼우려는 뜻은 아니다 (CasePhotoPromptClient와 같은 구조).
 */
public interface PipelineClient {

	/** 서비스 주소가 설정돼 있는지. 꺼져 있으면 사건 등록만 막고 나머지 기능은 그대로 쓴다 */
	boolean enabled();

	/** 작업을 만들고 시작한다. 파이프라인은 수십 분 걸리므로 서비스가 202로 바로 답한다 */
	PipelineJob createJob(PipelineJobRequest request);

	/**
	 * 대본 블록 재생성 작업을 만들고 시작한다 (BE-129 2차). 등록 작업과 같은 서비스 · 같은 "한 번에 하나" 규칙이라 다른 작업이
	 * 돌면 {@link PipelineException.Conflict}다. 상태는 {@link #getJob}, 지우기는 {@link #deleteJobQuietly}를 같이 쓴다
	 */
	PipelineJob createRegenerationJob(PipelineRegenerationRequest request);

	/**
	 * 재생성 결과 — 서비스 결과의 {@code regenerated}(다시 만든 블록 · 메타 · 검수 결과). 작업이 SUCCEEDED일 때만 있다.
	 * 형식은 파이프라인이 정하므로 {@link JsonNode}로 받는다({@link PipelineResult}와 같은 이유)
	 */
	JsonNode getRegenerationResult(String jobId);

	/** 작업 상태 (화면이 2초마다 묻는 A-15가 이 값을 쓴다) */
	PipelineJob getJob(String jobId);

	/**
	 * 생성 결과 (적재의 입력, BE-93 2차). 작업이 SUCCEEDED일 때만 쓸모가 있다.
	 *
	 * <p>판결문 원문은 들어 있지 않다 (FR-5-3). 적재에 쓰는 원문은 백엔드가 들고 있던 값이다.
	 */
	PipelineResult getResult(String jobId);

	/**
	 * 작업이 남긴 것을 지운다 (판결문 원문 포함).
	 *
	 * <p>지우지 못해도 예외를 던지지 않는다 — 정리 실패가 등록 흐름을 막지 않게 한다.
	 * 남은 작업은 COMMON-51(오래된 작업 자동 정리)이 마지막으로 치운다.
	 */
	void deleteJobQuietly(String jobId);
}
