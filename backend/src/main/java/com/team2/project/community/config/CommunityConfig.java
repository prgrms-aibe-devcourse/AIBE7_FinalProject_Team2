package com.team2.project.community.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 커뮤니티 설정 바인딩 등록 (BE-115) */
@Configuration
@EnableConfigurationProperties(CommunityProperties.class)
public class CommunityConfig {
}
