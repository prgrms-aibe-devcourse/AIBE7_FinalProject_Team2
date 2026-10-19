package com.team2.project.imagegen;

import org.springframework.stereotype.Component;

/**
 * 사건 대표사진(신문용) · 사건 발단 사진 프롬프트를 만든다 (BE-89, COMMON-41).
 *
 * 등장인물은 얼굴 없는 회색 엑스트라로 그리고, 내로 · 남불(고양이 · 불꽃 마스코트)은 이 사진들에
 * 등장시키지 않는다 — 결정 근거는 docs/decisions/COMMON-41.md. 그림체(내로 · 남불 세계관과 같은
 * 따뜻한 색감 · 플랫 셰이딩 · 종이 질감)는 실제 생성 테스트로 다듬어야 한다(아직 확정값 아님).
 */
@Component
public class CaseImagePromptBuilder {

	private static final String STYLE_SUFFIX = "Warm flat cel-shaded illustration style, soft muted cream and beige "
		+ "color palette, paper-textured background, thick clean linework, soft lighting, gentle "
		+ "storybook mood. All human figures are anonymous extras: featureless gray mannequin-like "
		+ "humans with no facial features (no eyes, nose, or mouth), plain rounded heads, neutral simple "
		+ "clothing. Do not depict any specific identifiable face. Do not include any cat or flame "
		+ "mascot characters. No text, no letters, no logos in the image.";

	/** @param caseSummary 비식별화한 사건 요약(영문 번역본 또는 영문으로 지어낸 요약) */
	public String buildRepresentativePhoto(String caseSummary) {
		return "Newspaper-style representative photo illustrating this case: " + requireText(caseSummary, "caseSummary")
			+ ". " + STYLE_SUFFIX;
	}

	/** @param incidentDescription 비식별화한 사건 발단 상황 설명(영문) */
	public String buildIncidentPhoto(String incidentDescription) {
		return "Illustration depicting the moment the incident began: "
			+ requireText(incidentDescription, "incidentDescription") + ". " + STYLE_SUFFIX;
	}

	private static String requireText(String text, String name) {
		if (text == null || text.isBlank()) {
			throw new ImageGenException(name + "가 비어 있습니다");
		}
		return text;
	}
}
