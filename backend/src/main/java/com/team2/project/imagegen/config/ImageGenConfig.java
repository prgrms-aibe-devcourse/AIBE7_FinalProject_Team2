package com.team2.project.imagegen.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ImageGenProperties.class)
public class ImageGenConfig {

	/** Cloudflare Workers AI 전용 클라이언트. 연결 · 응답 대기 시간은 cloudflare.imagegen.timeout */
	@Bean
	public RestClient cloudflareRestClient(ImageGenProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.timeout());
		String accountId = properties.accountId() == null ? "" : properties.accountId();
		String baseUrl = "https://api.cloudflare.com/client/v4/accounts/%s/ai".formatted(accountId);
		return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
	}
}
