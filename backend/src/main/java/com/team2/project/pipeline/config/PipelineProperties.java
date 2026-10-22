package com.team2.project.pipeline.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI 판결 파이프라인 서비스 연동 설정 (BE-93, API 명세 A-14 · A-15).
 *
 * <p>파이프라인 자체는 같은 서버의 내부 컨테이너로 돈다(COMMON-50). 백엔드는 그 서비스에 작업을 맡기고
 * 상태만 물어본다. 주소는 운영에서 Compose 내부 주소(http://pipeline:8000)로 들어온다.
 *
 * <p>주소가 없으면 <b>사건 등록만 꺼지고 서버는 정상으로 뜬다</b>. 파이프라인 오버레이를 켜지 않은 서버
 * (지금 운영이 그렇다 — deploy/docker-compose.pipeline.yml 머리말)에서도 나머지 기능은 그대로 쓰기 위해서다.
 *
 * @param baseUrl      파이프라인 서비스 주소. 비어 있으면 사건 등록 기능이 꺼진다
 * @param models       AI 판결 생성(generate 단계)에 쓸 모델. "공급자:모델ID" 형식이고, 여러 개면 모델마다 생성한다
 * @param extractModel 비식별화 · 구조화(extract 단계) 모델. 비우면 파이프라인 기본값(claude-opus-5-5)을 쓴다.
 *                     <b>이 단계에는 판결문 원문이 들어가므로 유료 등급 키의 모델만 쓴다</b>
 * @param timeout      작업 생성 · 상태 조회 호출의 대기 시간. 파이프라인은 202로 바로 답하므로 길 필요가 없다
 *                     (실제 생성은 수십 분이지만 그건 서비스가 뒤에서 돌린다)
 */
@ConfigurationProperties("pipeline-service")
public record PipelineProperties(
	String baseUrl,
	@DefaultValue(DEFAULT_MODEL) List<String> models,
	String extractModel,
	@DefaultValue("30s") Duration timeout
) {

	static final String DEFAULT_MODEL = "gemini:gemini-3.7-flash";

	/**
	 * 빈 값을 기본값으로 되돌린다.
	 *
	 * <p>@DefaultValue는 <b>값이 아예 없을 때만</b> 쓰인다. 운영 Compose는 `.env`에 없는 값을 빈 문자열로
	 * 넘기므로(`${PIPELINE_MODELS:-}`), 그대로 두면 모델 없이 파이프라인을 불러 "generate 모델이 없습니다"로
	 * 거절당한다 — 설정을 건드린 적 없는 서버에서 사건 등록만 안 되는, 알아채기 어려운 고장이다.
	 */
	public PipelineProperties {
		List<String> cleaned = models == null ? List.of()
			: models.stream().filter(model -> model != null && !model.isBlank()).map(String::trim).toList();
		models = cleaned.isEmpty() ? List.of(DEFAULT_MODEL) : cleaned;
	}

	public boolean enabled() {
		return baseUrl != null && !baseUrl.isBlank();
	}

	@Override
	public String toString() {
		return "PipelineProperties[baseUrl=" + (enabled() ? baseUrl : "(없음)") + ", models=" + models
			+ ", extractModel=" + (extractModel == null ? "(파이프라인 기본값)" : extractModel)
			+ ", timeout=" + timeout + "]";
	}
}
