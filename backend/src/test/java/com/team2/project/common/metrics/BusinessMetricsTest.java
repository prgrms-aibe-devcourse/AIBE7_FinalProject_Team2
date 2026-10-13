package com.team2.project.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BusinessMetricsTest {

	private SimpleMeterRegistry registry;
	private BusinessMetrics metrics;

	@BeforeEach
	void setUp() {
		registry = new SimpleMeterRegistry();
		metrics = new BusinessMetrics(registry);
	}

	@Test
	void recordExperienceStarted_incrementsCounter() {
		metrics.recordExperienceStarted();
		metrics.recordExperienceStarted();

		assertThat(registry.get("lawnambul.experience.started").counter().count()).isEqualTo(2);
	}

	@Test
	void recordVerdictConfirmed_incrementsCounter() {
		metrics.recordVerdictConfirmed();

		assertThat(registry.get("lawnambul.verdict.confirmed").counter().count()).isEqualTo(1);
	}

	@Test
	void recordAiCall_successAndFailure_areSeparateTimeSeriesUnderSameMetricName() {
		metrics.recordAiCallSuccess();
		metrics.recordAiCallSuccess();
		metrics.recordAiCallFailure();

		assertThat(registry.get("lawnambul.ai.call").tag("result", "success").counter().count()).isEqualTo(2);
		assertThat(registry.get("lawnambul.ai.call").tag("result", "failure").counter().count()).isEqualTo(1);
	}
}
