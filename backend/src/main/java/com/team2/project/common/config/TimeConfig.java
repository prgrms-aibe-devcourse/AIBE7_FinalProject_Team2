package com.team2.project.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각은 Clock으로 주입받는다 (테스트에서 시각을 고정할 수 있도록). 저장 시각은 UTC Instant
 */
@Configuration
public class TimeConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
