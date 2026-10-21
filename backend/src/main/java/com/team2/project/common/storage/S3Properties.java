package com.team2.project.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 파일 저장소 S3 설정 (COMMON-47). 자격 증명은 여기서 받지 않는다 — AWS SDK 기본 자격 증명 체인
 * (운영은 EC2 IAM 역할, 로컬은 AWS_PROFILE/환경변수)을 그대로 쓴다.
 *
 * 버킷이 없으면 업로드만 꺼지고 서버는 정상 기동한다. 기본값을 비워 두는 건 의도적이다 — 환경변수를
 * 빠뜨린 로컬 실행이 운영 버킷에 잘못 쓰지 않게 하기 위해서다.
 */
@ConfigurationProperties("cloud.aws.s3")
public record S3Properties(String bucket, @DefaultValue("") String dirPrefix) {

	public boolean enabled() {
		return bucket != null && !bucket.isBlank();
	}

	/** dirPrefix가 있으면 키 앞에 붙이고, "/"로 끝나도록 정리한다 */
	public String resolveKey(String key) {
		if (dirPrefix == null || dirPrefix.isBlank()) {
			return key;
		}
		String prefix = dirPrefix.endsWith("/") ? dirPrefix : dirPrefix + "/";
		return prefix + key;
	}
}
