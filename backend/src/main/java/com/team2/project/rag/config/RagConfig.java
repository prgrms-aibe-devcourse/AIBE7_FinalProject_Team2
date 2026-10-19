package com.team2.project.rag.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

	/** Gemini API 전용 클라이언트. 연결 · 응답 대기 시간은 rag.embedding.timeout */
	@Bean
	public RestClient geminiRestClient(RagProperties properties) {
		RagProperties.Embedding embedding = properties.embedding();
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(embedding.timeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(embedding.timeout());
		return RestClient.builder().baseUrl(embedding.baseUrl()).requestFactory(requestFactory).build();
	}
}
