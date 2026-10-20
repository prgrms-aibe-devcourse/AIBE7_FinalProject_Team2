package com.team2.project.imagegen;

/** 생성된 사건 사진을 올려 두는 곳 (BE-91). 지금은 S3 하나뿐이지만 구현을 바꿀 수 있게 인터페이스로 둔다 */
public interface CaseImageStorage {

	/** 설정(버킷)이 있어 저장할 수 있는지 */
	boolean enabled();

	/**
	 * @param image 저장할 이미지 바이트
	 * @param keySuffix 버킷 안 경로(접두사 뒤). 예: "case-7/incident-1.jpg"
	 * @param contentType 예: "image/jpeg"
	 * @throws ImageGenException 꺼져 있거나 업로드가 실패했을 때
	 */
	UploadedImage upload(byte[] image, String keySuffix, String contentType);

	/**
	 * @param key S3 객체 키 (재생성 · 삭제 때 사용, 사용자에게 내려주지 않는다)
	 * @param url 프론트에 내려주는 공개 URL
	 */
	record UploadedImage(String key, String url) {
	}
}
