package com.team2.project.rag.embedding;

/**
 * 검색어 임베딩 (task type RETRIEVAL_QUERY). 판례 임베딩(RETRIEVAL_DOCUMENT)과 같은 모델 · 차원이어야 한다
 */
public interface QueryEmbeddingClient {

	/** 설정(API 키)이 있어 임베딩을 만들 수 있는지 */
	boolean enabled();

	/** 모델 이름 (판례 행의 embedding_model과 비교한다) */
	String model();

	/**
	 * @param text 비식별화한 텍스트만 넣는다 (원문 · 사건번호 · 사용자 입력은 넣지 않는다, 8-1)
	 * @throws EmbeddingException 꺼져 있거나 호출 · 응답이 실패했을 때
	 */
	float[] embedQuery(String text);
}
