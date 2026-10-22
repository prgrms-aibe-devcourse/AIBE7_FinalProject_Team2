package com.team2.project.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

/** 사건 등록 배치 지연 지표 (7.3.1 경보가 쓴다) */
class CaseImportMetricsTest {

	@Test
	@DisplayName("진행 중인 작업이 없으면 0을 낸다")
	void noActiveImports_isZero() {
		CaseImportRepository repository = mock(CaseImportRepository.class);
		when(repository.findByStatusIn(any())).thenReturn(List.of());
		SimpleMeterRegistry registry = new SimpleMeterRegistry();

		new CaseImportMetrics(registry, repository);

		assertThat(registry.get("lawnambul.case_import.oldest_running_seconds").gauge().value()).isZero();
	}

	@Test
	@DisplayName("가장 오래된 진행 중 작업의 경과 시간(초)을 낸다")
	void activeImports_usesOldestCreatedAt() {
		CaseImportRepository repository = mock(CaseImportRepository.class);
		Instant now = Instant.now();
		when(repository.findByStatusIn(any()))
			.thenReturn(List.of(caseImport(now.minusSeconds(30)), caseImport(now.minusSeconds(600))));
		SimpleMeterRegistry registry = new SimpleMeterRegistry();

		new CaseImportMetrics(registry, repository);

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
