package com.team2.project.rag.embedding;

import com.team2.project.rag.config.RagProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Gemini embedContent로 검색어 임베딩을 만든다 (BE-51). 오프라인 도구의 llm.embed(batchEmbedContents)와 같은 모델 · 차원을 쓴다.
 *
 * - 429(한도) · 503(과부하) · 그 밖의 5xx · 연결 실패는 rag.embedding.backoff부터 두 배씩 기다렸다가 다시 보낸다
 * - 400 · 401 · 403 등은 다시 보내도 같으므로 바로 실패한다
 * - 응답 차원이 설정과 다르면 실패한다 (판례 벡터와 비교할 수 없다)
 */
@Slf4j
@Component
public class GeminiQueryEmbeddingClient implements QueryEmbeddingClient {

	private static final String TASK_TYPE = "RETRIEVAL_QUERY";

	private final RestClient restClient;
	private final RagProperties.Embedding properties;
	private final Sleeper sleeper;

	@Autowired
	public GeminiQueryEmbeddingClient(@Qualifier("geminiRestClient") RestClient restClient, RagProperties properties) {
		this(restClient, properties, Sleeper.THREAD);
	}

	GeminiQueryEmbeddingClient(RestClient restClient, RagProperties properties, Sleeper sleeper) {
		this.restClient = restClient;
		this.properties = properties.embedding();
		this.sleeper = sleeper;
	}

	@Override
	public boolean enabled() {
		return properties.enabled();
	}

	@Override
	public String model() {
		return properties.model();
	}

	@Override
	public float[] embedQuery(String text) {
		if (!enabled()) {
			throw new EmbeddingException("RAG 검색어 임베딩이 꺼져 있습니다 (GEMINI_API_KEY 없음)");
		}
		if (text == null || text.isBlank()) {
			throw new EmbeddingException("임베딩할 텍스트가 비어 있습니다");
		}
		Map<String, Object> body = Map.of(
			"model", "models/" + properties.model(),
			"content", Map.of("parts", List.of(Map.of("text", text))),
			"taskType", TASK_TYPE,
			"outputDimensionality", properties.dimensions());
		Duration wait = properties.backoff();
		for (int attempt = 1; ; attempt++) {
			try {
				return toVector(call(body));
			} catch (HttpStatusCodeException e) {
				if (!retryable(e.getStatusCode()) || attempt >= properties.maxAttempts()) {
					throw new EmbeddingException("임베딩 API 오류 (" + e.getStatusCode().value() + ", " + attempt + "번 시도)", e);
				}
				log.warn("임베딩 API {} 응답, {}ms 뒤 다시 보냄 ({}/{})", e.getStatusCode().value(), wait.toMillis(), attempt,
					properties.maxAttempts());
			} catch (ResourceAccessException e) {
				if (attempt >= properties.maxAttempts()) {
					throw new EmbeddingException("임베딩 API에 연결하지 못했습니다 (" + attempt + "번 시도)", e);
				}
				log.warn("임베딩 API 연결 실패, {}ms 뒤 다시 보냄 ({}/{})", wait.toMillis(), attempt, properties.maxAttempts());
			} catch (RestClientException e) {
				throw new EmbeddingException("임베딩 API 응답을 읽을 수 없습니다", e);
			}
			sleeper.sleep(wait);
			wait = wait.multipliedBy(2);
		}
	}

	private EmbedResponse call(Map<String, Object> body) {
		return restClient.post()
			.uri("/models/{model}:embedContent", properties.model())
			.header("x-goog-api-key", properties.apiKey())
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.body(EmbedResponse.class);
	}

	private float[] toVector(EmbedResponse response) {
		List<Double> values = response == null || response.embedding() == null ? null : response.embedding().values();
		if (values == null || values.size() != properties.dimensions()) {
			throw new EmbeddingException("임베딩 차원이 " + properties.dimensions() + "이 아닙니다 ("
				+ (values == null ? "없음" : values.size()) + ")");
		}
		float[] vector = new float[values.size()];
		for (int i = 0; i < vector.length; i++) {
			vector[i] = values.get(i).floatValue();
		}
		return vector;
	}

	private static boolean retryable(HttpStatusCode status) {
		return status.value() == 429 || status.is5xxServerError();
	}

	record EmbedResponse(Values embedding) {
	}

	record Values(List<Double> values) {
	}

	/** 재시도 대기. 테스트에서 실제로 기다리지 않게 바꾼다 */
	interface Sleeper {

		Sleeper THREAD = duration -> {
			try {
				Thread.sleep(duration.toMillis());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new EmbeddingException("임베딩 재시도 대기 중 중단되었습니다", e);
			}
		};

		void sleep(Duration duration);
	}
}
