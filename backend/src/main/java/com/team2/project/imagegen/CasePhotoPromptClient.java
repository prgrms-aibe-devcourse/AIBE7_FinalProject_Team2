package com.team2.project.imagegen;

/**
 * Gemini로 한글 텍스트를 짓는다 (BE-91 재설계, COMMON-41). 원래 "사건 사진 프롬프트 추천"용으로
 * 만들었지만(이름이 그대로 남아 있다 — 추후 재검토, `docs/decisions/BE-92.md`), 실제로는 지시문만 받아
 * 텍스트를 돌려주는 범용 클라이언트라 BE-92(신문 지면 콘텐츠 생성)에서도 그대로 재사용한다.
 */
public interface CasePhotoPromptClient {

	/** 설정(API 키)이 있어 텍스트를 지을 수 있는지 */
	boolean enabled();

	/**
	 * @param instruction 지시문(한글)
	 * @return 모델이 지은 한글 텍스트
	 * @throws ImageGenException 꺼져 있거나 호출 · 응답이 실패했을 때
	 */
	String generateText(String instruction);

	/**
	 * @param instruction 지시문(한글)
	 * @param maxOutputTokens 응답 최대 토큰 수 (기본 400보다 긴 출력이 필요할 때, 예: 기사 본문)
	 * @return 모델이 지은 한글 텍스트
	 * @throws ImageGenException 꺼져 있거나 호출 · 응답이 실패했을 때
	 */
	String generateText(String instruction, int maxOutputTokens);
}
