package com.team2.project.common.storage;

import java.net.URL;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@RequiredArgsConstructor
public class S3FileStorage implements FileStorage {

	private final S3Client s3Client;
	private final S3Properties properties;

	@Override
	public boolean enabled() {
		return properties.enabled();
	}

	@Override
	public UploadedFile upload(byte[] data, String key, String contentType) {
		if (!enabled()) {
			throw new FileStorageException("파일 저장이 꺼져 있습니다 (S3 버킷 설정 없음)");
		}
		if (key == null || key.isBlank()) {
			throw new FileStorageException("업로드 경로(key)가 비어 있습니다");
		}
		String resolvedKey = properties.resolveKey(key);
		try {
			PutObjectRequest request = PutObjectRequest.builder()
				.bucket(properties.bucket())
				.key(resolvedKey)
				.contentType(contentType)
				.contentLength((long) data.length)
				.build();
			s3Client.putObject(request, RequestBody.fromBytes(data));
			URL url = s3Client.utilities()
				.getUrl(GetUrlRequest.builder().bucket(properties.bucket()).key(resolvedKey).build());
			return new UploadedFile(key, url.toString());
		} catch (SdkException e) {
			throw new FileStorageException("파일 업로드에 실패했습니다", e);
		}
	}
}
