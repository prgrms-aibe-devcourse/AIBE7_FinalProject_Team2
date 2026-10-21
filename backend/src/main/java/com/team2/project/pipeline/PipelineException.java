package com.team2.project.pipeline;

/**
 * 파이프라인 서비스 호출 실패 (BE-93). 서비스 층(AdminCaseImportService)이 ErrorCode로 바꾼다.
 *
 * <p>메시지에 판결문 원문을 담지 않는다 — 이 메시지는 로그와 관리자 응답으로 흘러간다.
 */
public class PipelineException extends RuntimeException {

	public PipelineException(String message) {
		super(message);
	}

	public PipelineException(String message, Throwable cause) {
		super(message, cause);
	}

	/** 파이프라인이 이미 다른 작업을 돌리는 중 (서비스가 409로 답한다) → A-14 IMPORT_IN_PROGRESS */
	public static class Conflict extends PipelineException {

		public Conflict(String message) {
			super(message);
		}
	}

	/** 그런 작업이 없다 (서비스가 404로 답한다). 서비스 컨테이너를 볼륨째 지웠을 때 등 */
	public static class NotFound extends PipelineException {

		public NotFound(String message) {
			super(message);
		}
	}
}
