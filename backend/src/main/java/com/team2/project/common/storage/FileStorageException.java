package com.team2.project.common.storage;

/** 파일 저장 실패. 메시지에는 파일 내용 · 자격 증명을 넣지 않는다 */
public class FileStorageException extends RuntimeException {

	public FileStorageException(String message) {
		super(message);
	}

	public FileStorageException(String message, Throwable cause) {
		super(message, cause);
	}
}
