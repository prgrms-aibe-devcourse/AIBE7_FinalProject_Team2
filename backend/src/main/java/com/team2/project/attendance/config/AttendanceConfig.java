package com.team2.project.attendance.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 출석 설정 바인딩 등록 (BE-97) */
@Configuration
@EnableConfigurationProperties(AttendanceProperties.class)
public class AttendanceConfig {
}
