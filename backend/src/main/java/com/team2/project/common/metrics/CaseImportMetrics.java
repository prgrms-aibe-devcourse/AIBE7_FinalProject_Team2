package com.team2.project.common.metrics;

import com.team2.project.admin.caseimport.domain.CaseImport;
import com.team2.project.admin.caseimport.domain.CaseImportStatus;
import com.team2.project.admin.caseimport.repository.CaseImportRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 사건 등록(BE-93) 배치 지연 지표 (7.3.1 "배치 지연" 경보가 쓴다). 아직 끝나지 않은(QUEUED · RUNNING)
 * 작업 중 가장 오래 걸리고 있는 것의 경과 시간을 초 단위로 낸다.
 *
 * 값은 1분마다({@link #refresh()}) 미리 계산해 {@link #cachedSeconds}에 캐시해 두고, Gauge는 그 캐시만
 * 읽는다 — 스크레이프할 때마다 직접 DB를 조회하지 않는다(PR #262 리뷰 지적). 스크레이프 시점에 DB를
 * 직접 조회하면, 정작 `DbConnectionPoolNearExhaustion`이 울려야 할 순간(커넥션 풀이 가득 찬 순간)에
 * 이 조회가 커넥션을 기다리다 스크레이프 타임아웃을 넘겨 **그 스크레이프의 모든 지표가 함께 빠지고**
 * 정작 필요한 경보조차 계산되지 않는 문제가 생긴다 — 운영이 외부 DB(Supabase, BE-59)에 작은 커넥션
 * 풀(5개)로 붙어 있어 더 쉽게 일어난다.
 */
@Component
public class CaseImportMetrics {

	private static final List<CaseImportStatus> ACTIVE_STATUSES = List.of(CaseImportStatus.QUEUED,
		CaseImportStatus.RUNNING);

	private final CaseImportRepository repository;
	private final AtomicLong cachedSeconds = new AtomicLong(0);

	public CaseImportMetrics(MeterRegistry registry, CaseImportRepository repository) {
		this.repository = repository;
		Gauge.builder("lawnambul.case_import.oldest_running_seconds", cachedSeconds, AtomicLong::get)
			.description("아직 끝나지 않은 사건 등록 작업 중 가장 오래된 것의 경과 시간(초, 1분 간격 캐시). 진행 중인 작업이 없으면 0")
			.register(registry);
	}

	/** 1분마다 다시 계산한다. 스크레이프 주기(15s)보다 느슨해도 "오래 멈춰 있다"를 보는 경보(10분 기준)엔 충분하다 */
	@Scheduled(fixedRate = 60_000)
	void refresh() {
		long seconds = repository.findByStatusIn(ACTIVE_STATUSES).stream()
			.map(CaseImport::getCreatedAt)
			.min(Instant::compareTo)
			.map(oldest -> Duration.between(oldest, Instant.now()).toSeconds())
			.orElse(0L);
		cachedSeconds.set(seconds);
	}
}
