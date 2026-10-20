package com.team2.project.imagegen;

/**
 * 사건 사진 한글 프롬프트 추천 (BE-91 재설계, COMMON-41). 사진은 사람이 만들어 업로드하고,
 * 이 클라이언트는 그 사람이 참고할 한글 프롬프트 문구만 지어준다.
 */
public interface CasePhotoPromptClient {

	/** 설정(API 키)이 있어 프롬프트를 지을 수 있는지 */
	boolean enabled();

	/**
	 * @param instruction 사건 내용과 그림체 규칙을 담은 지시문(한글)
	 * @return 모델이 지은 한글 프롬프트 텍스트
	 * @throws ImageGenException 꺼져 있거나 호출 · 응답이 실패했을 때
	 */
	String generateText(String instruction);
}
