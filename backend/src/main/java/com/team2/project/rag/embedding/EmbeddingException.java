package com.team2.project.rag.embedding;

/** 검색어 임베딩 실패. 메시지에는 입력 텍스트 · API 키를 넣지 않는다 */
public class EmbeddingException extends RuntimeException {

	public EmbeddingException(String message) {
		super(message);
	}

	public EmbeddingException(String message, Throwable cause) {
		super(message, cause);
	}
}
