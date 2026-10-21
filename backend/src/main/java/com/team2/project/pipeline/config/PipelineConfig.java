package com.team2.project.pipeline.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PipelineProperties.class)
public class PipelineConfig {

	/**
	 * 파이프라인 서비스 전용 클라이언트. 연결 · 응답 대기 시간은 pipeline-service.timeout.
	 *
	 * <p>주소가 없어도 빈은 만든다 — 기능이 꺼진 것을 판단하는 쪽은 서비스(PipelineProperties.enabled)이고,
	 * 여기서 빈을 빼면 주입 대상이 사라져 서버가 뜨지 않는다.
	 */
	@Bean
	public RestClient pipelineRestClient(PipelineProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.timeout());
		RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
		if (properties.enabled()) {
			builder.baseUrl(properties.baseUrl());
		}
		return builder.build();
	}
}
