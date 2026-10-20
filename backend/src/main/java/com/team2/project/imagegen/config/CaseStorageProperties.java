package com.team2.project.imagegen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 생성된 사건 사진을 올릴 S3 버킷 설정 (BE-91). 결정: docs/decisions/BE-91.md
 *
 * 버킷이 없으면 저장만 꺼지고(서버는 정상 기동) 생성 API는 IMAGE_GENERATION_FAILED로 응답한다.
 * 자격 증명은 여기서 관리하지 않는다 — Spring Cloud AWS 기본 자격 증명 체인(환경변수 · IAM 역할)을 그대로 쓴다.
 */
@ConfigurationProperties("case-storage")
public record CaseStorageProperties(
	String bucket,
	@DefaultValue("case-photos/") String keyPrefix,
	String publicBaseUrl
) {

	public boolean enabled() {
		return bucket != null && !bucket.isBlank();
	}
}
