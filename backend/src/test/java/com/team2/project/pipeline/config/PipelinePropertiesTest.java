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

	@Test
	@DisplayName("대본 · 분류 모델은 기본으로 꺼져 있고, 빈 문자열도 꺼짐으로 본다 (BE-129)")
	void scriptAndClassifyOffByDefault() {
		assertThat(blank(null).scriptEnabled()).isFalse();
		assertThat(blank(null).classifyModel()).isNull();
		// 테스트 설정(application-test.yml)은 운영에서 켤 상태로 대본 모델을 켜 둔다 — 블록 재생성(A-13-15)을 시험하려고
		assertThat(properties.scriptEnabled()).isTrue();
		assertThat(properties.classifyModel()).isNull();
		PipelineProperties blankScript = new PipelineProperties("http://pipeline:8000", null, null,
			java.time.Duration.ofSeconds(30), "  ", "", null, null);
		assertThat(blankScript.scriptEnabled()).isFalse();
		assertThat(blankScript.classifyModel()).isNull();
	}

	@Test
	@DisplayName("사전 학습 점검은 기본 꺼짐이고, 값이 없거나 빈 문자열이면 꺼짐으로 본다 (BE-160)")
	void contaminationOffByDefault() {
		// 운영 기본값: 설정을 건드린 적 없는 서버에서 모델 호출이 늘지 않는다
		assertThat(properties.contaminationEnabled()).isFalse();
		assertThat(blank(null).contaminationEnabled()).isFalse();
		// Compose는 .env에 없는 값을 빈 문자열로 넘긴다(${PIPELINE_CONTAMINATION_ENABLED:-}) → 바인딩 실패 없이 꺼짐
		assertThat(bind(java.util.Map.of("pipeline-service.contamination-enabled", "")).contaminationEnabled()).isFalse();
		assertThat(bind(java.util.Map.of("pipeline-service.contamination-enabled", "true")).contaminationEnabled()).isTrue();
		assertThat(bind(java.util.Map.of("pipeline-service.contamination-enabled", "false")).contaminationEnabled()).isFalse();
	}

	@Test
	@DisplayName("RAG는 기본 꺼짐이고, 값이 없거나 빈 문자열이면 꺼짐으로 본다 (BE-161 2차)")
	void ragOffByDefault() {
		assertThat(properties.ragEnabled()).isFalse();
		assertThat(blank(null).ragEnabled()).isFalse();
		// Compose가 넘기는 빈 값(${PIPELINE_RAG_ENABLED:-})도 바인딩 실패 없이 꺼짐
		assertThat(bind(java.util.Map.of("pipeline-service.rag-enabled", "")).ragEnabled()).isFalse();
		assertThat(bind(java.util.Map.of("pipeline-service.rag-enabled", "true")).ragEnabled()).isTrue();
		// 두 스위치는 따로 논다
		assertThat(bind(java.util.Map.of("pipeline-service.rag-enabled", "true")).contaminationEnabled()).isFalse();
	}

	private static PipelineProperties bind(java.util.Map<String, String> values) {
		return new org.springframework.boot.context.properties.bind.Binder(
			new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(values))
			.bindOrCreate("pipeline-service", PipelineProperties.class);
	}

	private static PipelineProperties blank(java.util.List<String> models) {
		return new PipelineProperties("http://pipeline:8000", models, null, java.time.Duration.ofSeconds(30), null, null,
			null, null);
	}
}
