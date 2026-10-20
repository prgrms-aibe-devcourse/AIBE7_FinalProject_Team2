package com.team2.project.imagegen;

/**
 * 사건 사진(대표사진 · 발단 사진) 생성 (BE-89, COMMON-41). 등장인물은 얼굴 없는 회색 엑스트라로 그리고,
 * 내로 · 남불은 이 사진에 등장시키지 않는다 — 프롬프트는 {@link CaseImagePromptBuilder}로 만든다.
 */
public interface CaseImageGenClient {

	/** 설정(계정 ID · API 토큰)이 있어 이미지를 생성할 수 있는지 */
	boolean enabled();

	/** 생성에 쓰는 모델 이름 (예: @cf/black-forest-labs/flux-1-schnell). 감사 · 재생성 기록용(BE-91) */
	String model();

	/**
	 * @param prompt {@link CaseImagePromptBuilder}로 만든 영문 프롬프트
	 * @return 생성된 이미지(JPEG 등) 바이트. 저장(업로드) · DB 기록은 호출하는 쪽의 책임이다(BE-91)
	 * @throws ImageGenException 꺼져 있거나 호출 · 응답이 실패했을 때
	 */
	byte[] generateImage(String prompt);
}
