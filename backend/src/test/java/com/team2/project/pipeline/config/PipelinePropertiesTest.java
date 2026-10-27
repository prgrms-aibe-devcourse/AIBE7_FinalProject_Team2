package com.team2.project.pipeline.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.team2.project.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * application.yml의 pipeline-service 블록이 뜻대로 묶이는지 (BE-93).
 *
 * <p>`${PIPELINE_MODELS:gemini:gemini-3.7-flash}`처럼 기본값에 콜론이 들어 있어, 자리표시자가 잘못 잘리면
 * 모델 이름이 깨진 채로 운영에 나간다. 그러면 파이프라인이 "generate 모델이 없습니다"로 거절할 때까지
 * 아무도 모른다 — 그래서 기본값을 테스트로 고정해 둔다.
 */
class PipelinePropertiesTest extends ApiIntegrationTest {

	@Autowired PipelineProperties properties;

	@Test
	@DisplayName("모델 기본값은 공급자:모델ID 한 벌로 묶인다 (콜론이 잘리지 않는다)")
	void modelsDefault() {
		assertThat(properties.models()).containsExactly("gemini:gemini-3.7-flash");
	}

	@Test
	@DisplayName("주소를 주지 않은 환경에서는 꺼진 것으로 본다 — 사건 등록만 막히고 서버는 뜬다")
	void disabledByDefaultInTests() {
		assertThat(properties.enabled()).isFalse();
		assertThat(properties.extractModel()).isNullOrEmpty();
	}

	@Test
	@DisplayName("toString에 비밀값이 없다 (주소 · 모델 · 타임아웃만)")
	void toStringHasNoSecrets() {
		assertThat(properties.toString()).contains("baseUrl", "models", "timeout");
	}

	@Test
	@DisplayName("빈 값이 들어와도 기본 모델로 되돌린다 — Compose가 .env에 없는 값을 빈 문자열로 넘기기 때문")
	void blankModelsFallBackToDefault() {
		assertThat(blank(java.util.List.of()).models()).containsExactly("gemini:gemini-3.7-flash");
		assertThat(blank(java.util.List.of("  ")).models()).containsExactly("gemini:gemini-3.7-flash");
		assertThat(blank(null).models()).containsExactly("gemini:gemini-3.7-flash");
		// 제대로 준 값은 그대로 쓴다 (앞뒤 공백만 다듬는다)
		assertThat(blank(java.util.List.of(" openai:gpt-x ")).models()).containsExactly("openai:gpt-x");
	}

	private static PipelineProperties blank(java.util.List<String> models) {
		return new PipelineProperties("http://pipeline:8000", models, null, java.time.Duration.ofSeconds(30), null, null, null);
	}
}
