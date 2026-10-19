package com.team2.project.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

/**
 * Actuator는 관리 포트에서만 열리고 애플리케이션 포트에서는 닿지 않는다 (BE-66, WBS 7.2.1, #110 리뷰).
 * SecurityConfig나 management 설정이 나중에 바뀌어 scrape가 막히거나 애플리케이션 포트에 메트릭이 다시 열려도
 * 이 테스트가 바로 잡아낸다.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "management.server.port=0")
@AutoConfigureTestRestTemplate
@AutoConfigureMetrics
class ActuatorExposureTest {

	@LocalServerPort int appPort;
	@LocalManagementPort int managementPort;
	@Autowired TestRestTemplate rest;

	@Test
	void prometheus_onlyOnManagementPort() {
		assertThat(rest.getForEntity("http://localhost:" + managementPort + "/actuator/prometheus", String.class)
			.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(rest.getForEntity("http://localhost:" + appPort + "/actuator/prometheus", String.class)
			.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	/**
	 * 운영 Compose의 백엔드 healthcheck와 deploy.sh가 쓰는 readiness · liveness가 관리 포트에서 열린다 (BE-60, #161 리뷰).
	 * 404로 바뀌면 서버는 새 이미지를 항상 unhealthy로 보고 되돌리므로, 설정이 바뀌면 여기서 먼저 잡는다.
	 */
	@Test
	void probes_onManagementPort() {
		for (String probe : new String[] {"readiness", "liveness"}) {
			assertThat(rest.getForEntity("http://localhost:" + managementPort + "/actuator/health/" + probe, String.class)
				.getStatusCode()).as(probe).isEqualTo(HttpStatus.OK);
		}
	}
}
