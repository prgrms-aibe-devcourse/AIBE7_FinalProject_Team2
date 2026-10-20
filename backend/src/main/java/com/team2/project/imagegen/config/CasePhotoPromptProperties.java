package com.team2.project.imagegen.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 사건 사진 한글 프롬프트 추천 설정 (BE-91 재설계, COMMON-41). Gemini `generateContent`로 사건
 * 내용(overview · factor)을 바탕으로 사람이 쓸 한글 이미지 생성 프롬프트를 지어준다. 사진 자체는
 * 더 이상 AI가 직접 만들지 않고, 관리자가 이 프롬프트를 참고해 만든 이미지를 업로드한다
 * (결정: docs/decisions/BE-91.md).
 *
 * API 키가 없으면 프롬프트 추천만 꺼지고 서버는 정상으로 뜬다. RAG 임베딩과 같은 GEMINI_API_KEY를 쓴다
 * (둘 다 같은 Gemini 계정 키를 쓰되, 각자 설정 객체는 따로 둔다 — 임베딩 타임아웃을 바꿔도 이쪽에 영향이 없게).
 */
@ConfigurationProperties("case-photo-prompt")
public record CasePhotoPromptProperties(
	String apiKey,
	@DefaultValue("https://generativelanguage.googleapis.com/v1beta") String baseUrl,
	@DefaultValue("gemini-3.7-flash") String model,
	@DefaultValue("90s") Duration timeout
) {

	public boolean enabled() {
		return apiKey != null && !apiKey.isBlank();
	}

	@Override
	public String toString() {
		return "CasePhotoPromptProperties[apiKey=" + (enabled() ? "****" : "(없음)") + ", baseUrl=" + baseUrl
			+ ", model=" + model + ", timeout=" + timeout + "]";
	}
}
