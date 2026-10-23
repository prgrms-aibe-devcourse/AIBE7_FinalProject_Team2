package com.team2.project.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team2.project.admin.caseimport.domain.CaseImport;
import com.team2.project.admin.caseimport.repository.CaseImportRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 사건 등록 배치 지연 지표 (7.3.1 경보가 쓴다). 값은 {@link CaseImportMetrics#refresh()}가 1분마다
 * 캐시를 채운다 — Gauge는 등록 시점에는 DB를 조회하지 않는다(PR #262 리뷰, 스크레이프 때 DB를 직접
 * 조회하면 커넥션 풀 고갈 순간에 스크레이프 전체가 막히는 문제가 있었다).
 */
class CaseImportMetricsTest {

	@Test
	@DisplayName("refresh 전에는 0을 낸다(아직 캐시를 채우지 않음)")
	void beforeRefresh_isZero() {
		CaseImportRepository repository = mock(CaseImportRepository.class);
		SimpleMeterRegistry registry = new SimpleMeterRegistry();

		new CaseImportMetrics(registry, repository);

		assertThat(registry.get("lawnambul.case_import.oldest_running_seconds").gauge().value()).isZero();
		verifyNoInteractions(repository);	// 생성자에서는 DB를 조회하지 않는다
	}

	@Test
	@DisplayName("진행 중인 작업이 없으면 refresh 뒤에도 0을 낸다")
	void refresh_noActiveImports_isZero() {
		CaseImportRepository repository = mock(CaseImportRepository.class);
		when(repository.findByStatusIn(any())).thenReturn(List.of());
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		CaseImportMetrics metrics = new CaseImportMetrics(registry, repository);

		metrics.refresh();

		assertThat(registry.get("lawnambul.case_import.oldest_running_seconds").gauge().value()).isZero();
	}

	@Test
	@DisplayName("refresh는 가장 오래된 진행 중 작업의 경과 시간(초)을 캐시한다")
	void refresh_activeImports_usesOldestCreatedAt() {
		CaseImportRepository repository = mock(CaseImportRepository.class);
		Instant now = Instant.now();
		when(repository.findByStatusIn(any()))
			.thenReturn(List.of(caseImport(now.minusSeconds(30)), caseImport(now.minusSeconds(600))));
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		CaseImportMetrics metrics = new CaseImportMetrics(registry, repository);

		metrics.refresh();

		double seconds = registry.get("lawnambul.case_import.oldest_running_seconds").gauge().value();
		assertThat(seconds).isBetween(595.0, 605.0);
	}

	private static CaseImport caseImport(Instant createdAt) {
		try {
			Constructor<CaseImport> constructor = CaseImport.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			CaseImport caseImport = constructor.newInstance();
			Field field = CaseImport.class.getDeclaredField("createdAt");
			field.setAccessible(true);
			field.set(caseImport, createdAt);
			return caseImport;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
