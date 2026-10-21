package com.team2.project.common.storage;

/** 파일을 올려 두는 곳 (COMMON-47). 지금은 S3 하나뿐이지만 구현을 바꿀 수 있게 인터페이스로 둔다 */
public interface FileStorage {

	/** 설정(버킷)이 있어 저장할 수 있는지 */
	boolean enabled();

	/**
	 * @param data 저장할 바이트
	 * @param key 버킷 안 경로(dirPrefix 뒤). 예: "case-photos/case-7/representative.jpg"
	 * @param contentType 예: "image/jpeg"
	 * @throws FileStorageException 꺼져 있거나 업로드가 실패했을 때
	 */
	UploadedFile upload(byte[] data, String key, String contentType);

	/**
	 * @param key dirPrefix를 더하기 전의 키(호출한 쪽이 넘긴 값 그대로)
	 * @param url 공개 URL
	 */
	record UploadedFile(String key, String url) {
	}
}
