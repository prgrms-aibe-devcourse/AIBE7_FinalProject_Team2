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
import com.team2.project.imagegen.config.ImageGenProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Cloudflare Workers AI 사건 사진 생성 클라이언트 (BE-89). 네트워크 대신 MockRestServiceServer로 응답을 흉내 낸다
 */
class CloudflareCaseImageGenClientTest {

	private static final String BASE = "https://cf.test/accounts/acc-1/ai";
	private static final String URL = BASE + "/run/@cf/black-forest-labs/flux-1-schnell";

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
	@DisplayName("프롬프트를 보내고 base64 이미지를 바이트로 디코딩해 돌려준다")
	void generateImage_decodesBase64Image() {
		byte[] fakeImage = {1, 2, 3, 4};
		String base64 = Base64.getEncoder().encodeToString(fakeImage);
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer test-token"))
			.andExpect(jsonPath("$.prompt").value("a gray faceless extra in a newspaper illustration"))
			.andRespond(withSuccess("{\"success\":true,\"result\":{\"image\":\"" + base64 + "\"},\"errors\":[]}",
				MediaType.APPLICATION_JSON));

		byte[] result = client("acc-1", "test-token").generateImage("a gray faceless extra in a newspaper illustration");

		assertThat(result).containsExactly(fakeImage);
		assertThat(aiCallCount("success")).isEqualTo(1);
		assertThat(aiCallCount("failure")).isEqualTo(0);
		server.verify();
	}

	@Test
	@DisplayName("success가 false면 이미지가 있어도 실패로 본다")
	void generateImage_successFalse_isError() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess("{\"success\":false,\"result\":{},\"errors\":[\"model busy\"]}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client("acc-1", "test-token").generateImage("프롬프트"))
			.isInstanceOf(ImageGenException.class)
			.hasMessageContaining("실패를 나타냅니다");
		assertThat(aiCallCount("failure")).isEqualTo(1);
		server.verify();
	}

	@Test
	@DisplayName("success가 true인데 응답에 이미지가 없으면 실패한다")
	void generateImage_missingImage_isError() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess("{\"success\":true,\"result\":{},\"errors\":[]}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client("acc-1", "test-token").generateImage("프롬프트"))
			.isInstanceOf(ImageGenException.class)
			.hasMessageContaining("이미지가 없습니다");
		assertThat(aiCallCount("failure")).isEqualTo(1);
		server.verify();
	}

	@Test
	@DisplayName("호출 실패(5xx)는 그대로 예외로 알린다 (자동 재시도하지 않는다)")
	void generateImage_serverError_isNotRetried() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatThrownBy(() -> client("acc-1", "test-token").generateImage("프롬프트"))
			.isInstanceOf(ImageGenException.class)
			.hasMessageContaining("503");
		assertThat(aiCallCount("failure")).isEqualTo(1);
		server.verify();
	}

	@Test
	@DisplayName("계정 ID · API 토큰이 없으면 꺼져 있고, 호출하지 않는다")
	void notConfigured_isDisabled() {
		CloudflareCaseImageGenClient client = client("", "");

		assertThat(client.enabled()).isFalse();
		assertThatThrownBy(() -> client.generateImage("프롬프트")).isInstanceOf(ImageGenException.class)
			.hasMessageContaining("꺼져");
		server.verify();
	}

	@Test
	@DisplayName("프롬프트가 비어 있으면 호출 전에 실패한다")
	void blankPrompt_isRejectedBeforeCall() {
		assertThatThrownBy(() -> client("acc-1", "test-token").generateImage(" ")).isInstanceOf(ImageGenException.class)
			.hasMessageContaining("비어");
		server.verify();
	}

	@Test
	@DisplayName("설정 객체를 문자열로 찍어도 API 토큰 · 계정 ID가 그대로 나오지 않는다")
	void properties_toString_masksSecrets() {
		ImageGenProperties properties = new ImageGenProperties("acc-1", "secret-token",
			"@cf/black-forest-labs/flux-1-schnell", Duration.ofSeconds(1));

		assertThat(properties.toString()).doesNotContain("secret-token").doesNotContain("acc-1");
	}

	@Test
	@DisplayName("FLUX 계열이 아닌 모델은 설정 단계에서 거절한다 (응답 형식이 달라 해석하지 못한다)")
	void unsupportedModel_isRejectedAtConfig() {
		assertThatThrownBy(
			() -> new ImageGenProperties("acc-1", "test-token", "@cf/meta/llama-3", Duration.ofSeconds(60)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("flux-");
	}

	private CloudflareCaseImageGenClient client(String accountId, String apiToken) {
		ImageGenProperties properties = new ImageGenProperties(accountId, apiToken,
			"@cf/black-forest-labs/flux-1-schnell", Duration.ofSeconds(60));
		return new CloudflareCaseImageGenClient(restClient, properties, metrics);
	}
}
