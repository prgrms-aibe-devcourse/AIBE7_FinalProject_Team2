package com.team2.project.pipeline;

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

	/** 작업 상태 (화면이 2초마다 묻는 A-15가 이 값을 쓴다) */
	PipelineJob getJob(String jobId);

	/**
	 * 작업이 남긴 것을 지운다 (판결문 원문 포함).
	 *
	 * <p>지우지 못해도 예외를 던지지 않는다 — 정리 실패가 등록 흐름을 막지 않게 한다.
	 * 남은 작업은 COMMON-51(오래된 작업 자동 정리)이 마지막으로 치운다.
	 */
	void deleteJobQuietly(String jobId);
}
