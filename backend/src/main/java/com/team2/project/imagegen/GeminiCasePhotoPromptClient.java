package com.team2.project.imagegen;

import com.team2.project.common.metrics.BusinessMetrics;
import com.team2.project.imagegen.config.CasePhotoPromptProperties;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gemini `generateContent`로 사건 사진 한글 프롬프트를 짓는다 (BE-91 재설계). 호출 1회가 비용이
 * 작고 실패가 드물어, Cloudflare 이미지 생성 때와 같은 이유로 재시도는 하지 않는다 — 실패하면 바로 알린다.
 */
@Slf4j
@Component
public class GeminiCasePhotoPromptClient implements CasePhotoPromptClient {

	private final RestClient restClient;
	private final CasePhotoPromptProperties properties;
	private final BusinessMetrics metrics;

	@Autowired
	public GeminiCasePhotoPromptClient(@Qualifier("casePhotoPromptRestClient") RestClient restClient,
			CasePhotoPromptProperties properties, BusinessMetrics metrics) {
		this.restClient = restClient;
		this.properties = properties;
		this.metrics = metrics;
	}

	@Override
	public boolean enabled() {
		return properties.enabled();
	}

	@Override
	public String generateText(String instruction) {
		if (!enabled()) {
			throw new ImageGenException("사진 프롬프트 추천이 꺼져 있습니다 (GEMINI_API_KEY 없음)");
		}
		if (instruction == null || instruction.isBlank()) {
			throw new ImageGenException("프롬프트 지시문이 비어 있습니다");
		}
		long startedAt = System.currentTimeMillis();
		try {
			String text = extractText(call(instruction));
			metrics.recordAiCallSuccess();
			return text;
		} catch (ImageGenException e) {
			metrics.recordAiCallFailure();
			throw e;
		} catch (RestClientResponseException e) {
			metrics.recordAiCallFailure();
			log.warn("프롬프트 추천 API 오류: status={}, body={}, 경과={}ms", e.getStatusCode().value(),
				e.getResponseBodyAsString(), System.currentTimeMillis() - startedAt);
			throw new ImageGenException("프롬프트 추천 API 오류 (" + e.getStatusCode().value() + ")", e);
		} catch (ResourceAccessException e) {
			metrics.recordAiCallFailure();
			log.warn("프롬프트 추천 API에 연결하지 못했습니다: {}, 경과={}ms, 설정 타임아웃={}", e.getMessage(),
				System.currentTimeMillis() - startedAt, properties.timeout());
			throw new ImageGenException("프롬프트 추천 API에 연결하지 못했습니다", e);
		} catch (RestClientException e) {
			metrics.recordAiCallFailure();
			log.warn("프롬프트 추천 API 응답을 읽을 수 없습니다: {}, 경과={}ms", e.getMessage(),
				System.currentTimeMillis() - startedAt);
			throw new ImageGenException("프롬프트 추천 API 응답을 읽을 수 없습니다", e);
		}
	}

	private GenerateContentResponse call(String instruction) {
		Map<String, Object> body = Map.of(
			"contents", List.of(Map.of("parts", List.of(Map.of("text", instruction)))),
			"generationConfig", Map.of("temperature", 0.7, "maxOutputTokens", 400));
		return restClient.post()
			.uri("/models/{model}:generateContent", properties.model())
			.header("x-goog-api-key", properties.apiKey())
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.body(GenerateContentResponse.class);
	}

	private static String extractText(GenerateContentResponse response) {
		String text = response == null || response.candidates() == null || response.candidates().isEmpty() ? null
			: firstText(response.candidates().get(0));
		if (text == null || text.isBlank()) {
			String finishReason = response == null || response.candidates() == null || response.candidates().isEmpty()
				? null
				: response.candidates().get(0).finishReason();
			log.warn("프롬프트 추천 응답에 텍스트가 없습니다: promptFeedback={}, finishReason={}",
				response == null ? null : response.promptFeedback(), finishReason);
			throw new ImageGenException("프롬프트 추천 응답에 텍스트가 없습니다");
		}
		return text.strip();
	}

	private static String firstText(Candidate candidate) {
		if (candidate.content() == null || candidate.content().parts() == null || candidate.content().parts().isEmpty()) {
			return null;
		}
		return candidate.content().parts().get(0).text();
	}

	record GenerateContentResponse(List<Candidate> candidates, Object promptFeedback) {
	}

	record Candidate(Content content, String finishReason) {
	}

	record Content(List<Part> parts) {
	}

	record Part(String text) {
	}
}
