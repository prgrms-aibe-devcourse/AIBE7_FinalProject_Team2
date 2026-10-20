package com.team2.project.imagegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.team2.project.common.metrics.BusinessMetrics;
import com.team2.project.imagegen.config.CasePhotoPromptProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Gemini generateContent 사진 프롬프트 추천 클라이언트 (BE-91 재설계). 네트워크 대신 MockRestServiceServer로 흉내 낸다
 */
class GeminiCasePhotoPromptClientTest {

	private static final String BASE = "https://gemini.test/v1beta";
	private static final String URL = BASE + "/models/gemini-3.7-flash:generateContent";

	private MockRestServiceServer server;
	private RestClient restClient;
	private SimpleMeterRegistry meterRegistry;
	private BusinessMetrics metrics;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		restClient = builder.build();
		meterRegistry = new SimpleMeterRegistry();
		metrics = new BusinessMetrics(meterRegistry);
	}

	private double aiCallCount(String result) {
		return meterRegistry.get("lawnambul.ai.call").tag("result", result).counter().count();
	}

	@Test
	@DisplayName("지시문을 보내고 첫 후보의 텍스트를 돌려준다")
	void generateText_returnsFirstCandidateText() {
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("x-goog-api-key", "test-key"))
			.andExpect(jsonPath("$.contents[0].parts[0].text").value("지시문"))
			.andRespond(withSuccess(
				"{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"  추천 프롬프트  \"}]}}]}", MediaType.APPLICATION_JSON));

		String text = client("test-key").generateText("지시문");

		assertThat(text).isEqualTo("추천 프롬프트");
		assertThat(aiCallCount("success")).isEqualTo(1);
		assertThat(aiCallCount("failure")).isEqualTo(0);
		server.verify();
	}

	@Test
	@DisplayName("응답에 후보가 없으면 실패한다 (재시도하지 않는다)")
	void generateText_noCandidates_isError() {
		server.expect(requestTo(URL)).andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client("test-key").generateText("지시문")).isInstanceOf(ImageGenException.class)
			.hasMessageContaining("텍스트가 없습니다");
		assertThat(aiCallCount("failure")).isEqualTo(1);
		server.verify();
	}

	@Test
	@DisplayName("호출 실패(5xx)는 재시도 없이 바로 예외로 알린다")
	void generateText_serverError_isNotRetried() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatThrownBy(() -> client("test-key").generateText("지시문")).isInstanceOf(ImageGenException.class)
			.hasMessageContaining("503");
		assertThat(aiCallCount("failure")).isEqualTo(1);
		server.verify();
	}

	@Test
	@DisplayName("API 키가 없으면 꺼져 있고, 호출하지 않는다")
	void noApiKey_isDisabled() {
		GeminiCasePhotoPromptClient client = client("");

		assertThat(client.enabled()).isFalse();
		assertThatThrownBy(() -> client.generateText("지시문")).isInstanceOf(ImageGenException.class)
			.hasMessageContaining("꺼져");
		server.verify();
	}

	@Test
	@DisplayName("지시문이 비어 있으면 호출 전에 실패한다")
	void blankInstruction_isRejectedBeforeCall() {
		assertThatThrownBy(() -> client("test-key").generateText(" ")).isInstanceOf(ImageGenException.class)
			.hasMessageContaining("비어");
		server.verify();
	}

	@Test
	@DisplayName("설정 객체를 문자열로 찍어도 API 키가 그대로 나오지 않는다")
	void properties_toString_masksApiKey() {
		CasePhotoPromptProperties properties = new CasePhotoPromptProperties("secret-key", BASE, "gemini-3.7-flash",
			Duration.ofSeconds(1));

		assertThat(properties.toString()).doesNotContain("secret-key").contains("apiKey=****");
	}

	private GeminiCasePhotoPromptClient client(String apiKey) {
		CasePhotoPromptProperties properties = new CasePhotoPromptProperties(apiKey, BASE, "gemini-3.7-flash",
			Duration.ofSeconds(1));
		return new GeminiCasePhotoPromptClient(restClient, properties, metrics);
	}
}
