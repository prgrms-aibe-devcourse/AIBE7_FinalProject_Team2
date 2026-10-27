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
 * @param scriptModel  사건 대본(증거조사 대사) 생성 모델 (BE-107 · BE-108). <b>비우면 대본을 만들지 않는다</b> —
 *                     판단 요소마다 모델을 한 번씩 더 부르므로, 설정을 건드린 적 없는 서버에서 호출이 늘지 않게
 *                     기본을 꺼짐으로 둔다. 켜면 등록이 끝날 때 대본이 비공개로 함께 적재된다(관리자 검수 뒤 공개)
 * @param scriptCast   배역. {@code prosecutor} · {@code defense}에 캐릭터 id(naero · nambul)를 넣는다.
 *                     기본은 사건 1 대본과 같은 내로 = 검사 · 남불 = 변호인이다(BE-107은 배역을 자동으로 정하지 않는다)
 */
@ConfigurationProperties("pipeline-service")
public record PipelineProperties(
	String baseUrl,
	@DefaultValue(DEFAULT_MODEL) List<String> models,
	String extractModel,
	@DefaultValue("30s") Duration timeout,
	String scriptModel,
	@DefaultValue("naero") String scriptProsecutor,
	@DefaultValue("nambul") String scriptDefense
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
		// 빈 문자열로 넘어온 배역은 기본값으로 되돌린다(models와 같은 이유 — Compose가 빈 값을 넘긴다).
		// 파이프라인은 배역이 비었거나 둘이 같으면 설정 오류로 거절한다(script_generation.py)
		scriptProsecutor = blankToDefault(scriptProsecutor, "naero");
		scriptDefense = blankToDefault(scriptDefense, "nambul");
	}

	private static String blankToDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	/** 대본을 만들 설정인지. 모델이 없으면 등록은 지금까지처럼 대본 없이 끝난다 */
	public boolean scriptEnabled() {
		return scriptModel != null && !scriptModel.isBlank();
	}

	public boolean enabled() {
		return baseUrl != null && !baseUrl.isBlank();
	}

	@Override
	public String toString() {
		return "PipelineProperties[baseUrl=" + (enabled() ? baseUrl : "(없음)") + ", models=" + models
			+ ", extractModel=" + (extractModel == null ? "(파이프라인 기본값)" : extractModel)
			+ ", timeout=" + timeout
			+ ", scriptModel=" + (scriptEnabled() ? scriptModel : "(대본 생성 끔)") + "]";
	}
}
