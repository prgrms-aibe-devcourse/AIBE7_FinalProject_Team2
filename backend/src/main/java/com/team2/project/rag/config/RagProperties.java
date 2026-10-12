package com.team2.project.rag.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI 판결 RAG 유사 판례 검색 설정 (BE-51). 결정: docs/ai-judgment-pipeline.md 8-1
 *
 * 저장(판례) 임베딩은 오프라인 도구(tools/ai-judgment/rag_store.py)가 만들고, 서버는 검색어 임베딩만 만든다.
 * 두 쪽이 같은 모델 · 차원을 써야 비교할 수 있다. API 키가 없으면 RAG만 꺼지고 서버는 정상으로 뜬다.
 */
@ConfigurationProperties("rag")
public record RagProperties(
	@DefaultValue Embedding embedding,
	@DefaultValue Search search
) {

	/**
	 * @param apiKey Gemini API 키. 환경변수 GEMINI_API_KEY로만 받는다 (코드 · 저장소에 두지 않는다)
	 * @param maxAttempts 한 요청의 최대 시도 횟수 (첫 시도 포함). 429 · 503 · 5xx · 연결 실패만 다시 보낸다
	 * @param backoff 재시도 사이 기본 대기 (시도마다 두 배)
	 */
	public record Embedding(
		String apiKey,
		@DefaultValue("https://generativelanguage.googleapis.com/v1beta") String baseUrl,
		@DefaultValue("gemini-embedding-001") String model,
		@DefaultValue("3072") int dimensions,
		@DefaultValue("10s") Duration timeout,
		@DefaultValue("3") int maxAttempts,
		@DefaultValue("1s") Duration backoff
	) {

		public boolean enabled() {
			return apiKey != null && !apiKey.isBlank();
		}

		/** 기본 toString은 모든 값을 찍으므로 API 키를 가린다 (설정 객체가 로그에 남아도 키가 노출되지 않게) */
		@Override
		public String toString() {
			return "Embedding[apiKey=" + (enabled() ? "****" : "(없음)") + ", baseUrl=" + baseUrl + ", model=" + model
				+ ", dimensions=" + dimensions + ", timeout=" + timeout + ", maxAttempts=" + maxAttempts
				+ ", backoff=" + backoff + "]";
		}
	}

	/**
	 * @param topK 돌려줄 최대 판례 수 (사건 그룹당 1건)
	 * @param minScore 코사인 유사도 하한. 예시값 0.6으로 시작해 샘플 사건 10건의 상위 점수를 보고 확정한다 (8-1)
	 * @param maxScore 이 이상이면 같은 사건으로 보고 뺀다(사건번호 해시로 못 잡은 자기 사건 · 공범 별도 판결 대비).
	 *                 판례끼리 요약 유사도는 같은 사건 0.97 ~ 0.98 · 다른 사건 최고 0.93이었다. 검색어(사건 개요)와 판례의
	 *                 점수는 이보다 낮게 나오므로 샘플 사건으로 다시 맞춘다
	 */
	public record Search(
		@DefaultValue("5") int topK,
		@DefaultValue("0.6") double minScore,
		@DefaultValue("0.95") double maxScore
	) {
	}
}
