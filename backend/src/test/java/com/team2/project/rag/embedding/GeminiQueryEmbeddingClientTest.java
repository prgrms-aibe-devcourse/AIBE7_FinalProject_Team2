package com.team2.project.rag.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.team2.project.common.metrics.BusinessMetrics;
import com.team2.project.rag.config.RagProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Gemini 검색어 임베딩 클라이언트 (BE-51). 네트워크 대신 MockRestServiceServer로 응답을 흉내 낸다
 */
class GeminiQueryEmbeddingClientTest {

	private static final String BASE = "https://gemini.test/v1beta";
	private static final String URL = BASE + "/models/gemini-embedding-001:embedContent";

	private MockRestServiceServer server;
	private RestClient restClient;
	private final List<Duration> waits = new ArrayList<>();
	private SimpleMeterRegistry meterRegistry;
	private BusinessMetrics metrics;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		restClient = builder.build();
		waits.clear();
		meterRegistry = new SimpleMeterRegistry();
		metrics = new BusinessMetrics(meterRegistry);
	}

	private double aiCallCount(String result) {
		return meterRegistry.get("lawnambul.ai.call").tag("result", result).counter().count();
	}

	@Test
	@DisplayName("RETRIEVAL_QUERY · 설정 차원으로 텍스트 하나만 보내고 벡터를 돌려준다")
	void embedQuery_sendsQueryTaskAndText() {
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("x-goog-api-key", "test-key"))
			.andExpect(jsonPath("$.taskType").value("RETRIEVAL_QUERY"))
			.andExpect(jsonPath("$.outputDimensionality").value(3))
			.andExpect(jsonPath("$.model").value("models/gemini-embedding-001"))
			.andExpect(jsonPath("$.content.parts[0].text").value("비식별화한 사건 개요"))
			.andExpect(jsonPath("$.content.parts.length()").value(1))
			.andRespond(withSuccess(embeddingJson(3), MediaType.APPLICATION_JSON));

		float[] vector = client("test-key", 3, 3).embedQuery("비식별화한 사건 개요");

		assertThat(vector).containsExactly(0.001f, 0.002f, 0.003f);
		assertThat(aiCallCount("success")).isEqualTo(1);
		assertThat(aiCallCount("failure")).isEqualTo(0);
		server.verify();
	}

	@Test
	@DisplayName("429 · 503은 두 배씩 기다렸다가 다시 보낸다")
	void embedQuery_retriesRateLimitAndOverload() {
		server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		server.expect(ExpectedCount.once(), requestTo(URL))
			.andRespond(withSuccess(embeddingJson(3), MediaType.APPLICATION_JSON));

		assertThat(client("k", 3, 3).embedQuery("개요")).hasSize(3);
		assertThat(waits).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
		// 재시도 두 번은 "아직 포기하지 않은 중간 단계"라 실패로 세지 않는다. 최종 성공만 1건 기록된다
		assertThat(aiCallCount("success")).isEqualTo(1);
		assertThat(aiCallCount("failure")).isEqualTo(0);
		server.verify();
	}

	@Test
	@DisplayName("최대 시도 횟수를 넘기면 실패한다. 오류 메시지에 입력 텍스트 · 키를 넣지 않는다")
	void embedQuery_givesUpAfterMaxAttempts() {
		server.expect(ExpectedCount.times(2), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatThrownBy(() -> client("secret-key", 3, 2).embedQuery("피고인 개요 원문"))
			.isInstanceOf(EmbeddingException.class)
			.hasMessageContaining("503")
			.hasMessageNotContaining("secret-key")
			.hasMessageNotContaining("피고인 개요 원문");
		assertThat(aiCallCount("failure")).isEqualTo(1);
		assertThat(aiCallCount("success")).isEqualTo(0);
		server.verify();
	}

	@Test
	@DisplayName("429의 Retry-After(초)만큼 기다리고, 30초를 넘으면 기다리지 않고 실패한다")
	void embedQuery_honorsRetryAfter() {
		HttpHeaders shortWait = new HttpHeaders();
		shortWait.add(HttpHeaders.RETRY_AFTER, "2");
		server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(shortWait));
		server.expect(ExpectedCount.once(), requestTo(URL))
			.andRespond(withSuccess(embeddingJson(3), MediaType.APPLICATION_JSON));

		assertThat(client("k", 3, 3).embedQuery("개요")).hasSize(3);
		assertThat(waits).containsExactly(Duration.ofSeconds(2));
		server.verify();
	}

	@Test
	@DisplayName("Retry-After가 30초를 넘으면(일 한도 등) 기다리지 않고 실패한다")
	void embedQuery_longRetryAfter_failsFast() {
		HttpHeaders longWait = new HttpHeaders();
		longWait.add(HttpHeaders.RETRY_AFTER, "3600");
		server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(longWait));

		assertThatThrownBy(() -> client("k", 3, 3).embedQuery("개요")).isInstanceOf(EmbeddingException.class)
			.hasMessageContaining("3600초");
		assertThat(waits).isEmpty();
		server.verify();
	}

	@Test
	@DisplayName("비표준 상태 코드도 상태 코드로 알린다 (응답을 읽을 수 없음으로 뭉개지 않는다)")
	void embedQuery_unknownStatus_reportsStatus() {
		server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withRawStatus(499));

		assertThatThrownBy(() -> client("k", 3, 3).embedQuery("개요")).isInstanceOf(EmbeddingException.class)
			.hasMessageContaining("499");
		server.verify();
	}

	@Test
	@DisplayName("400 · 403은 다시 보내지 않는다")
	void embedQuery_clientError_isNotRetried() {
		server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

		assertThatThrownBy(() -> client("k", 3, 3).embedQuery("개요")).isInstanceOf(EmbeddingException.class)
			.hasMessageContaining("403");
		assertThat(waits).isEmpty();
		server.verify();
	}

	@Test
	@DisplayName("응답 차원이 설정과 다르면 실패한다 (판례 벡터와 비교할 수 없다)")
	void embedQuery_wrongDimension_isError() {
		server.expect(requestTo(URL)).andRespond(withSuccess(embeddingJson(768), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client("k", 3072, 3).embedQuery("개요")).isInstanceOf(EmbeddingException.class)
			.hasMessageContaining("차원");
		assertThat(aiCallCount("failure")).isEqualTo(1);	// 응답은 받았지만 차원 검증에서 거절된 경우도 실패로 센다
		assertThat(aiCallCount("success")).isEqualTo(0);
	}

	@Test
	@DisplayName("API 키가 없으면 꺼져 있고, 호출하지 않는다")
	void noApiKey_isDisabled() {
		GeminiQueryEmbeddingClient client = client("", 3, 3);

		assertThat(client.enabled()).isFalse();
		assertThatThrownBy(() -> client.embedQuery("개요")).isInstanceOf(EmbeddingException.class)
			.hasMessageContaining("꺼져");
		server.verify();
	}

	@Test
	@DisplayName("설정 객체를 문자열로 찍어도 API 키가 나오지 않는다")
	void properties_toString_masksApiKey() {
		RagProperties.Embedding embedding = new RagProperties.Embedding("secret-key", BASE, "gemini-embedding-001", 3,
			Duration.ofSeconds(1), 3, Duration.ofMillis(100));

		assertThat(embedding.toString()).doesNotContain("secret-key").contains("apiKey=****");
		assertThat(new RagProperties(embedding, new RagProperties.Search(5, 0.6, 0.95)).toString()).doesNotContain("secret-key");
	}

	private GeminiQueryEmbeddingClient client(String apiKey, int dimensions, int maxAttempts) {
		RagProperties properties = new RagProperties(
			new RagProperties.Embedding(apiKey, BASE, "gemini-embedding-001", dimensions, Duration.ofSeconds(1),
				maxAttempts, Duration.ofMillis(100)),
			new RagProperties.Search(5, 0.6, 0.95));
		return new GeminiQueryEmbeddingClient(restClient, properties, waits::add, metrics);
	}

	private static String embeddingJson(int dimensions) {
		String values = IntStream.rangeClosed(1, dimensions).mapToObj(i -> String.valueOf(i / 1000.0))
			.collect(Collectors.joining(","));
		return "{\"embedding\":{\"values\":[" + values + "]}}";
	}
}
