package com.team2.project.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 업로드 (COMMON-47). AWS SDK v2를 직접 쓴다 — Spring Cloud AWS 같은 자동 설정 레이어를 더하지 않는다.
 * 리전만 설정으로 받고, 자격 증명은 SDK 기본 체인(DefaultCredentialsProvider)을 그대로 쓴다.
 */
@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

	@Bean
	public S3Client s3Client(@Value("${cloud.aws.region:ap-northeast-2}") String region) {
		return S3Client.builder().region(Region.of(region)).build();
	}
}
