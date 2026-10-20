package com.team2.project.imagegen.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(CasePhotoPromptProperties.class)
public class CasePhotoPromptConfig {

	/** Gemini generateContent 전용 클라이언트. 연결 · 응답 대기 시간은 case-photo-prompt.timeout */
	@Bean
	public RestClient casePhotoPromptRestClient(CasePhotoPromptProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.timeout());
		return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
	}
}
