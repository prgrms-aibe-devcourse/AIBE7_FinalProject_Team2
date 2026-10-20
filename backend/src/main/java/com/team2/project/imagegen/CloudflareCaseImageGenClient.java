package com.team2.project.imagegen;

import com.team2.project.common.metrics.BusinessMetrics;
import com.team2.project.imagegen.config.ImageGenProperties;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cloudflare Workers AI(`/run/{model}`)로 사건 사진을 생성한다 (BE-89). Spring AI는 Cloudflare를
 * 공식 지원하지 않아 RestClient로 직접 호출한다(결정: docs/decisions/BE-89.md).
 *
 * - 재시도는 하지 않는다. 이미지 생성은 (임베딩과 달리) 비용이 커서, 한도 초과 때 자동으로 다시 보내면
 *   비용이 배로 늘 수 있다 — 실패하면 호출한 쪽에 바로 알린다.
 */
@Slf4j
@Component
public class CloudflareCaseImageGenClient implements CaseImageGenClient {

	private final RestClient restClient;
	private final ImageGenProperties properties;
	private final BusinessMetrics metrics;

	@Autowired
	public CloudflareCaseImageGenClient(@Qualifier("cloudflareRestClient") RestClient restClient,
			ImageGenProperties properties, BusinessMetrics metrics) {
		this.restClient = restClient;
		this.properties = properties;
		this.metrics = metrics;
	}

	@Override
	public boolean enabled() {
		return properties.enabled();
	}

	@Override
	public byte[] generateImage(String prompt) {
		if (!enabled()) {
			throw new ImageGenException("사건 사진 생성이 꺼져 있습니다 (Cloudflare 계정 ID · API 토큰 없음)");
		}
		if (prompt == null || prompt.isBlank()) {
			throw new ImageGenException("이미지 생성 프롬프트가 비어 있습니다");
		}
		try {
			RunResponse response = call(prompt);
			byte[] image = toImageBytes(response);
			metrics.recordAiCallSuccess();
			return image;
		} catch (ImageGenException e) {
			metrics.recordAiCallFailure();
			throw e;
		} catch (RestClientResponseException e) {
			metrics.recordAiCallFailure();
			throw new ImageGenException("이미지 생성 API 오류 (" + e.getStatusCode().value() + ")", e);
		} catch (ResourceAccessException e) {
			metrics.recordAiCallFailure();
			throw new ImageGenException("이미지 생성 API에 연결하지 못했습니다", e);
		} catch (RestClientException e) {
			metrics.recordAiCallFailure();
			throw new ImageGenException("이미지 생성 API 응답을 읽을 수 없습니다", e);
		}
	}

	private RunResponse call(String prompt) {
		// 모델명(예: @cf/black-forest-labs/flux-1-schnell)에 "/"가 들어 있어 경로 변수로 넘기면
		// 한 세그먼트로 보고 "%2F"로 인코딩해 버린다. 문자열을 직접 이어붙여 그대로 둔다.
		return restClient.post()
			.uri("/run/" + properties.model())
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("prompt", prompt))
			.retrieve()
			.body(RunResponse.class);
	}

	private byte[] toImageBytes(RunResponse response) {
		if (response != null && !response.success()) {
			throw new ImageGenException("이미지 생성 응답이 실패를 나타냅니다 (errors=" + response.errors() + ")");
		}
		String base64 = response == null || response.result() == null ? null : response.result().image();
		if (base64 == null || base64.isBlank()) {
			List<Object> errors = response == null ? null : response.errors();
			throw new ImageGenException("이미지 생성 응답에 이미지가 없습니다 (errors=" + errors + ")");
		}
		try {
			return Base64.getDecoder().decode(base64);
		} catch (IllegalArgumentException e) {
			throw new ImageGenException("이미지 생성 응답의 base64 디코딩에 실패했습니다", e);
		}
	}

	record RunResponse(boolean success, Result result, List<Object> errors) {
	}

	record Result(String image) {
	}
}
