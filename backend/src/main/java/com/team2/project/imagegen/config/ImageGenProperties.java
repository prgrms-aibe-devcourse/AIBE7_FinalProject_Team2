package com.team2.project.imagegen.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 사건 사진 생성 설정 (BE-89). 결정: docs/decisions/COMMON-41.md, docs/decisions/BE-89.md
 *
 * Spring AI는 Cloudflare Workers AI를 공식 지원하지 않아 RestClient로 직접 호출한다(참고: imagegen 프로젝트).
 * 계정 ID · API 토큰이 없으면 이미지 생성만 꺼지고 서버는 정상으로 뜬다.
 */
@ConfigurationProperties("cloudflare.imagegen")
public record ImageGenProperties(
	String accountId,
	String apiToken,
	@DefaultValue("@cf/black-forest-labs/flux-1-schnell") String model,
	@DefaultValue("60s") Duration timeout
) {

	/**
	 * `CloudflareCaseImageGenClient`는 응답을 `{ "result": { "image": "<base64>" } }` 형식(FLUX 계열)으로만
	 * 해석한다. 다른 계열 모델은 응답 형식이 달라(예: `result`가 문자열) 그대로 쓰면 읽지 못한다 — 모델을 바꾸려면
	 * 응답 처리 코드도 같이 확인해야 한다.
	 */
	private static final String SUPPORTED_MODEL_PREFIX = "@cf/black-forest-labs/flux-";

	public ImageGenProperties {
		if (model != null && !model.isBlank() && !model.startsWith(SUPPORTED_MODEL_PREFIX)) {
			throw new IllegalStateException(
				"cloudflare.imagegen.model은 " + SUPPORTED_MODEL_PREFIX + "* 계열만 지원합니다 (현재: " + model
					+ "). 다른 모델은 응답 형식이 달라 CloudflareCaseImageGenClient의 역직렬화 코드를 먼저 확인해야 합니다.");
		}
	}

	public boolean enabled() {
		return accountId != null && !accountId.isBlank() && apiToken != null && !apiToken.isBlank();
	}

	/** 기본 toString은 모든 값을 찍으므로 계정 ID · API 토큰을 가린다 */
	@Override
	public String toString() {
		return "ImageGenProperties[accountId=" + (accountId == null || accountId.isBlank() ? "(없음)" : "****")
			+ ", apiToken=" + (apiToken == null || apiToken.isBlank() ? "(없음)" : "****") + ", model=" + model
			+ ", timeout=" + timeout + "]";
	}
}
