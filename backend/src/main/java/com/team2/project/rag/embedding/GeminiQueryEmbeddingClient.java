package com.team2.project.rag.embedding;

import com.team2.project.common.metrics.BusinessMetrics;
import com.team2.project.rag.config.RagProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gemini embedContent로 검색어 임베딩을 만든다 (BE-51). 오프라인 도구의 llm.embed(batchEmbedContents)와 같은 모델 · 차원을 쓴다.
 *
 * - 429(한도) · 503(과부하) · 그 밖의 5xx · 연결 실패는 rag.embedding.backoff부터 두 배씩 기다렸다가 다시 보낸다.
 *   Retry-After(초)가 있으면 그만큼 기다리고, 30초를 넘으면(일 한도 등) 기다리지 않고 실패한다
 * - 400 · 401 · 403 등은 다시 보내도 같으므로 바로 실패한다
 * - 응답 차원이 설정과 다르면 실패한다 (판례 벡터와 비교할 수 없다)
 */
@Slf4j
@Component
public class GeminiQueryEmbeddingClient implements QueryEmbeddingClient {

	private static final String TASK_TYPE = "RETRIEVAL_QUERY";
	/** Retry-After가 이보다 길면 기다리지 않고 실패한다 */
	static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(30);

	private final RestClient restClient;
	private final RagProperties.Embedding properties;
	private final Sleeper sleeper;
	private final BusinessMetrics metrics;

	@Autowired
	public GeminiQueryEmbeddingClient(@Qualifier("geminiRestClient") RestClient restClient, RagProperties properties,
			BusinessMetrics metrics) {
		this(restClient, properties, Sleeper.THREAD, metrics);
	}

	GeminiQueryEmbeddingClient(RestClient restClient, RagProperties properties, Sleeper sleeper, BusinessMetrics metrics) {
		this.restClient = restClient;
		this.properties = properties.embedding();
		this.sleeper = sleeper;
		this.metrics = metrics;
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
				float[] vector = toVector(call(body));
				metrics.recordAiCallSuccess();
				return vector;
			} catch (EmbeddingException e) {
				// 응답은 받았지만 차원이 설정과 달라 toVector가 거절한 경우도 호출 실패다 (재시도하지 않는다)
				metrics.recordAiCallFailure();
				throw e;
			} catch (RestClientResponseException e) {  // HttpStatusCodeException · UnknownHttpStatusCodeException(비표준 코드) 모두
				int status = e.getStatusCode().value();
				if (!retryable(e.getStatusCode()) || attempt >= properties.maxAttempts()) {
					metrics.recordAiCallFailure();
					throw new EmbeddingException("임베딩 API 오류 (" + status + ", " + attempt + "번 시도)", e);
				}
				Duration retryAfter = retryAfter(e);
				if (retryAfter != null && retryAfter.compareTo(MAX_RETRY_AFTER) > 0) {
					// 일 한도처럼 오래 기다려야 풀리는 한도는 기다리지 않는다 (요청 스레드를 붙잡지 않게)
					metrics.recordAiCallFailure();
					throw new EmbeddingException("임베딩 API 한도 (" + status + "), " + retryAfter.toSeconds() + "초 뒤에 풀린다고 안내함", e);
				}
				if (retryAfter != null && retryAfter.compareTo(wait) > 0) {
					wait = retryAfter;
				}
				log.warn("임베딩 API {} 응답, {}ms 뒤 다시 보냄 ({}/{})", status, wait.toMillis(), attempt, properties.maxAttempts());
			} catch (ResourceAccessException e) {
				if (attempt >= properties.maxAttempts()) {
					metrics.recordAiCallFailure();
					throw new EmbeddingException("임베딩 API에 연결하지 못했습니다 (" + attempt + "번 시도)", e);
				}
				log.warn("임베딩 API 연결 실패, {}ms 뒤 다시 보냄 ({}/{})", wait.toMillis(), attempt, properties.maxAttempts());
			} catch (RestClientException e) {
				metrics.recordAiCallFailure();
				throw new EmbeddingException("임베딩 API 응답을 읽을 수 없습니다", e);
			}
			try {
				sleeper.sleep(wait);
			} catch (EmbeddingException e) {
				// 재시도 대기 중 중단(스레드 인터럽트 등)도 이 호출의 최종 결과는 실패다.
				// 여기서 세지 않으면 성공 · 실패 어디에도 잡히지 않아 성공률의 분모에서 빠진다 (#110 리뷰)
				metrics.recordAiCallFailure();
				throw e;
			}
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

	/** 응답의 Retry-After(초). 없거나 읽을 수 없으면 null (날짜 형식은 쓰지 않는다) */
	private static Duration retryAfter(RestClientResponseException e) {
		String value = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
		if (value == null) {
			return null;
		}
		try {
			long seconds = Long.parseLong(value.trim());
			return seconds < 0 ? null : Duration.ofSeconds(seconds);
		} catch (NumberFormatException ignored) {
			return null;
		}
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
