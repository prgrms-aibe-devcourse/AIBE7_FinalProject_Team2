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
