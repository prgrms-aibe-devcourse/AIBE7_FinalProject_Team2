package com.team2.project.common.config;

import io.micrometer.core.instrument.config.MeterFilter;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 커스텀 메트릭 필터 (BE-70, WBS 7.2.2, #120 리뷰).
 */
@Configuration
public class MetricsConfig {

	/**
	 * 관리 엔드포인트(/actuator/**) 요청은 http.server.requests에서 뺀다. Prometheus가 15초마다 스크레이프하는
	 * /actuator/prometheus 자체 호출이 사용자 지연 지표(7.3.1 p95 경보 대상)에 섞이지 않게 하기 위해서다.
	 */
	@Bean
	public MeterFilter excludeActuatorRequestsMeterFilter() {
		return MeterFilter.deny(id -> "http.server.requests".equals(id.getName())
			&& Optional.ofNullable(id.getTag("uri")).map(uri -> uri.startsWith("/actuator")).orElse(false));
	}
}
