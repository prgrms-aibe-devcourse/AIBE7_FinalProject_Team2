package com.team2.project.imagegen;

/** 사건 사진 생성 실패. 메시지에는 프롬프트 · API 토큰을 넣지 않는다 */
public class ImageGenException extends RuntimeException {

	public ImageGenException(String message) {
		super(message);
	}

	public ImageGenException(String message, Throwable cause) {
		super(message, cause);
	}
}
