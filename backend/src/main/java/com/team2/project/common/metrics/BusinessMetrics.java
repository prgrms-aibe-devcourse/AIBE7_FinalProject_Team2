package com.team2.project.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 커스텀 비즈니스 메트릭 (BE-66, WBS 7.2.1): 체험 시작 · 판결 확정 · AI 호출 성공률.
 *
 * 사건 ID · 체험 ID 같은 값은 태그로 넣지 않는다. Prometheus는 태그 조합마다 별도 시계열을 만들기 때문에,
 * 값의 종류가 계속 늘어나는 ID를 태그로 쓰면 시계열 수가 무한히 늘어나 Grafana Cloud 무료 등급 한도를 넘긴다(WBS R9).
 */
@Component
public class BusinessMetrics {

	private final Counter experienceStarted;
	private final Counter verdictConfirmed;
	private final Counter aiCallSuccess;
	private final Counter aiCallFailure;

	public BusinessMetrics(MeterRegistry registry) {
		this.experienceStarted = Counter.builder("lawnambul.experience.started")
			.description("새 체험이 시작된 횟수 (이미 있던 체험을 다시 돌려준 경우는 세지 않는다)")
			.register(registry);
		this.verdictConfirmed = Counter.builder("lawnambul.verdict.confirmed")
			.description("판결이 확정된 횟수")
			.register(registry);
		this.aiCallSuccess = Counter.builder("lawnambul.ai.call")
			.description("AI(임베딩 등 외부 모델) 호출 결과")
			.tag("result", "success")
			.register(registry);
		this.aiCallFailure = Counter.builder("lawnambul.ai.call")
			.description("AI(임베딩 등 외부 모델) 호출 결과")
			.tag("result", "failure")
			.register(registry);
	}

	public void recordExperienceStarted() {
		experienceStarted.increment();
	}

	public void recordVerdictConfirmed() {
		verdictConfirmed.increment();
	}

	public void recordAiCallSuccess() {
		aiCallSuccess.increment();
	}

	public void recordAiCallFailure() {
		aiCallFailure.increment();
	}
}
