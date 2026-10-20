package com.team2.project.common.security.token.jwt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** JWT 설정 바인딩 등록 (확장 단계, BE-94 · WBS 7.4.3) */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
}
