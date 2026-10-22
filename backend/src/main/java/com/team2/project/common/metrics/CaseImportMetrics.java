package com.team2.project.common.metrics;

import com.team2.project.admin.caseimport.domain.CaseImport;
import com.team2.project.admin.caseimport.domain.CaseImportStatus;
import com.team2.project.admin.caseimport.repository.CaseImportRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Component;

/**
 * 사건 등록(BE-93) 배치 지연 지표 (7.3.1 "배치 지연" 경보가 쓴다). 아직 끝나지 않은(QUEUED · RUNNING)
 * 작업 중 가장 오래 걸리고 있는 것의 경과 시간을 초 단위로 낸다.
 *
 * Gauge는 스크레이프할 때마다(15s) 공급자 함수를 다시 불러 값을 구한다 — 별도 스케줄러 없이 DB를
 * 조회 한 번 하는 것으로 충분하다(관리자 전용 · 저빈도 기능이라 스크레이프 주기의 DB 조회는 부담이
 * 되지 않는다). 사건 등록 ID는 태그로 넣지 않는다(카디널리티 규칙, WBS R9) — 경과 시간 하나만 낸다.
 */
@Component
public class CaseImportMetrics {

	private static final List<CaseImportStatus> ACTIVE_STATUSES = List.of(CaseImportStatus.QUEUED,
		CaseImportStatus.RUNNING);

	public CaseImportMetrics(MeterRegistry registry, CaseImportRepository repository) {
		Gauge.builder("lawnambul.case_import.oldest_running_seconds", repository, oldestRunningSeconds())
			.description("아직 끝나지 않은 사건 등록 작업 중 가장 오래된 것의 경과 시간(초). 진행 중인 작업이 없으면 0")
			.register(registry);
	}

	private static ToDoubleFunction<CaseImportRepository> oldestRunningSeconds() {
		return repository -> repository.findByStatusIn(ACTIVE_STATUSES).stream()
			.map(CaseImport::getCreatedAt)
			.min(Instant::compareTo)
			.map(oldest -> (double) Duration.between(oldest, Instant.now()).toSeconds())
			.orElse(0.0);
	}
}
