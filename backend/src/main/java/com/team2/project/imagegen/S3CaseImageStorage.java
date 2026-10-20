package com.team2.project.imagegen;

import com.team2.project.imagegen.config.CaseStorageProperties;
import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * S3에 사건 사진을 올린다 (BE-91). 결정: docs/decisions/BE-91.md (버킷 · 리전은 환경변수, 자격 증명은
 * Spring Cloud AWS 기본 체인 — 운영 EC2는 IAM 역할, 로컬은 AWS_ACCESS_KEY_ID · AWS_SECRET_ACCESS_KEY)
 */
@Component
public class S3CaseImageStorage implements CaseImageStorage {

	private final S3Template s3Template;
	private final CaseStorageProperties properties;

	public S3CaseImageStorage(S3Template s3Template, CaseStorageProperties properties) {
		this.s3Template = s3Template;
		this.properties = properties;
	}

	@Override
	public boolean enabled() {
		return properties.enabled();
	}

	@Override
	public UploadedImage upload(byte[] image, String keySuffix, String contentType) {
		if (!enabled()) {
			throw new ImageGenException("사건 사진 저장이 꺼져 있습니다 (S3 버킷 설정 없음)");
		}
		if (keySuffix == null || keySuffix.isBlank()) {
			throw new ImageGenException("업로드 경로(keySuffix)가 비어 있습니다");
		}
		String key = properties.keyPrefix() + keySuffix;
		try {
			ObjectMetadata metadata = ObjectMetadata.builder().contentType(contentType).build();
			S3Resource resource = s3Template.upload(properties.bucket(), key, new ByteArrayInputStream(image), metadata);
			return new UploadedImage(key, publicUrl(key, resource));
		} catch (RuntimeException e) {
			throw new ImageGenException("사건 사진 업로드에 실패했습니다", e);
		}
	}

	private String publicUrl(String key, S3Resource resource) {
		String base = properties.publicBaseUrl();
		if (base != null && !base.isBlank()) {
			// CDN(CloudFront) 등 앞단이 있으면 그 도메인 기준으로 URL을 만든다
			return base.replaceAll("/+$", "") + "/" + key;
		}
		try {
			return resource.getURL().toString();
		} catch (IOException e) {
			throw new ImageGenException("업로드된 사진의 URL을 가져오지 못했습니다", e);
		}
	}
}
