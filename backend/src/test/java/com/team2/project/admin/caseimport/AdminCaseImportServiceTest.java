package com.team2.project.admin.caseimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team2.project.admin.audit.AdminAuditAction;
import com.team2.project.admin.audit.AdminAuditLogger;
import com.team2.project.admin.caseimport.domain.CaseImport;
import com.team2.project.admin.caseimport.domain.CaseImportStatus;
import com.team2.project.admin.caseimport.dto.CaseImportCreateRequest;
import com.team2.project.admin.caseimport.dto.CaseImportResponse;
import com.team2.project.admin.caseimport.repository.CaseImportRepository;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.legalcase.domain.CourtLevel;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.repository.CaseSourceRepository;
import com.team2.project.pipeline.PipelineClient;
import com.team2.project.pipeline.PipelineException;
import com.team2.project.pipeline.PipelineJob;
import com.team2.project.pipeline.PipelineJobRequest;
import com.team2.project.pipeline.config.PipelineProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AdminCaseImportServiceTest {

	private CaseImportRepository importRepository;
	private CaseSourceRepository caseSourceRepository;
	private PipelineClient pipelineClient;
	private AdminAuditLogger auditLogger;
	private AdminCaseImportService service;

	private final AdminPrincipal admin = mock(AdminPrincipal.class);
	private final HttpServletRequest httpRequest = mock(HttpServletRequest.class);

	@BeforeEach
	void setUp() {
		importRepository = mock(CaseImportRepository.class);
		caseSourceRepository = mock(CaseSourceRepository.class);
		pipelineClient = mock(PipelineClient.class);
		auditLogger = mock(AdminAuditLogger.class);
		when(pipelineClient.enabled()).thenReturn(true);
		when(caseSourceRepository.findExistingCaseNumbers(any())).thenReturn(List.of());
		when(importRepository.findCaseNumbersInUse(any(), any())).thenReturn(List.of());
		when(importRepository.findByStatusIn(any())).thenReturn(List.of());
		when(importRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		service = newService();
	}

	private AdminCaseImportService newService() {
		return new AdminCaseImportService(importRepository, caseSourceRepository, pipelineClient,
			new PipelineProperties("http://pipeline:8000", List.of("gemini:test"), null, Duration.ofSeconds(30)),
			auditLogger, mock(com.team2.project.admin.caseimport.load.CaseImportLoader.class),
			new NoOpTransactionManager());
	}

	// ---------------------------------------------------------------- A-14

	@Test
	@DisplayName("등록하면 파이프라인에 원문만 보내고, 작업 행과 감사 로그를 남긴다")
	void startImport_sendsOnlyOriginalText() {
		when(pipelineClient.createJob(any())).thenReturn(job("abc123", "QUEUED", List.of()));

		service.startImport(request(), admin, httpRequest);

		ArgumentCaptor<PipelineJobRequest> sent = ArgumentCaptor.forClass(PipelineJobRequest.class);
		verify(pipelineClient).createJob(sent.capture());
		assertThat(sent.getValue().sources()).singleElement()
			.satisfies(source -> assertThat(source.originalText()).isEqualTo("판결문 본문"));
		assertThat(sent.getValue().models()).containsExactly("gemini:test");
		verify(importRepository).save(any(CaseImport.class));
		verify(auditLogger).record(eq(admin), eq(AdminAuditAction.CASE_IMPORT), any(), any(), any(), eq(httpRequest));
	}

	@Test
	@DisplayName("감사 로그에 판결문 원문을 남기지 않는다 — 사건번호 · 심급만 (A-14)")
	void startImport_auditHasNoOriginalText() {
		when(pipelineClient.createJob(any())).thenReturn(job("abc123", "QUEUED", List.of()));

		service.startImport(request(), admin, httpRequest);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> detail = ArgumentCaptor.forClass(Map.class);
		verify(auditLogger).record(any(), any(), any(), any(), detail.capture(), any());
		assertThat(detail.getValue().toString())
			.doesNotContain("판결문 본문")
			.contains("2099고합001", "FIRST", "MURDER");
	}

	@Test
	@DisplayName("심급이 겹치면 400 VALIDATION_ERROR (파이프라인을 부르지 않는다)")
	void startImport_duplicateCourtLevel() {
		CaseImportCreateRequest request = new CaseImportCreateRequest(CrimeType.MURDER,
			List.of(source(CourtLevel.FIRST, "2099고합001"), source(CourtLevel.FIRST, "2099고합002")));

		assertThatThrownBy(() -> service.startImport(request, admin, httpRequest))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
		verifyNoInteractions(auditLogger);
		verify(pipelineClient, never()).createJob(any());
	}

	@Test
	@DisplayName("최종 확정 판결을 둘 이상 표시하면 400 (파이프라인에는 첫 번째만 넘어가 어긋난다)")
	void startImport_multipleFinalDecisions() {
		CaseImportCreateRequest request = new CaseImportCreateRequest(CrimeType.MURDER,
			List.of(source(CourtLevel.FIRST, "2099고합001"), source(CourtLevel.APPEAL, "2099노001")));

		assertThatThrownBy(() -> service.startImport(request, admin, httpRequest))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
		verify(pipelineClient, never()).createJob(any());
	}

	@Test
	@DisplayName("이미 등록한 사건번호면 409 DUPLICATE_SOURCE, 어느 항목인지 details로 알려준다")
	void startImport_duplicateSource() {
		when(caseSourceRepository.findExistingCaseNumbers(any())).thenReturn(List.of("2099고합002"));
		CaseImportCreateRequest request = new CaseImportCreateRequest(CrimeType.MURDER,
			List.of(source(CourtLevel.FIRST, "2099고합001", false), source(CourtLevel.APPEAL, "2099고합002")));

		assertThatThrownBy(() -> service.startImport(request, admin, httpRequest))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				BusinessException failure = (BusinessException) e;
				assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_SOURCE);
				assertThat(failure.getDetails()).singleElement()
					.satisfies(detail -> assertThat(detail.field()).isEqualTo("sources[1].caseNumber"));
			});
		verify(pipelineClient, never()).createJob(any());
	}

	@Test
	@DisplayName("진행 중인 등록이 정말 도는 중이면 409 IMPORT_IN_PROGRESS")
	void startImport_alreadyRunning() {
		when(importRepository.findByStatusIn(any())).thenReturn(List.of(running("other")));
		when(pipelineClient.getJob("other")).thenReturn(job("other", "RUNNING", List.of()));

		assertThatThrownBy(() -> service.startImport(request(), admin, httpRequest))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.IMPORT_IN_PROGRESS);
		verify(pipelineClient, never()).createJob(any());
	}

	@Test
	@DisplayName("아무도 묻지 않아 RUNNING으로 남은 등록이 실제로는 끝났으면 막지 않는다 (영원히 잠기지 않게)")
	void startImport_staleRunningRow_doesNotBlock() {
		CaseImport stale = running("stale");
		when(importRepository.findByStatusIn(any())).thenReturn(List.of(stale));
		when(importRepository.findById(any())).thenReturn(Optional.of(stale));
		when(pipelineClient.getJob("stale")).thenReturn(job("stale", "SUCCEEDED", List.of()));
		when(pipelineClient.createJob(any())).thenReturn(job("abc123", "QUEUED", List.of()));

		service.startImport(request(), admin, httpRequest);

		verify(pipelineClient).createJob(any());
		assertThat(stale.getStatus()).isEqualTo(CaseImportStatus.SUCCEEDED);	// 받아 적었다
	}

	@Test
	@DisplayName("파이프라인이 작업을 잃었으면 그 등록을 실패로 끝내고 새 등록을 받는다")
	void startImport_staleRowWithLostJob_doesNotBlock() {
		CaseImport stale = running("lost");
		when(importRepository.findByStatusIn(any())).thenReturn(List.of(stale));
		when(importRepository.findById(any())).thenReturn(Optional.of(stale));
		when(pipelineClient.getJob("lost")).thenThrow(new PipelineException.NotFound("없음"));
		when(pipelineClient.createJob(any())).thenReturn(job("abc123", "QUEUED", List.of()));

		service.startImport(request(), admin, httpRequest);

		verify(pipelineClient).createJob(any());
		assertThat(stale.getStatus()).isEqualTo(CaseImportStatus.FAILED);
	}

	@Test
	@DisplayName("진행 중인 등록을 확인할 수 없으면 막는다 (둘이 동시에 도는 것보다 안전)")
	void startImport_whenCannotCheck_blocks() {
		when(importRepository.findByStatusIn(any())).thenReturn(List.of(running("other")));
		when(pipelineClient.getJob("other")).thenThrow(new PipelineException("닿지 못함"));

		assertThatThrownBy(() -> service.startImport(request(), admin, httpRequest))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.IMPORT_IN_PROGRESS);
		verify(pipelineClient, never()).createJob(any());
	}

	@Test
	@DisplayName("파이프라인이 409로 거절하면(서비스 쪽 잠금) 409 IMPORT_IN_PROGRESS로 바꾼다")
	void startImport_pipelineConflict() {
		when(pipelineClient.createJob(any())).thenThrow(new PipelineException.Conflict("도는 중"));

		assertThatThrownBy(() -> service.startImport(request(), admin, httpRequest))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.IMPORT_IN_PROGRESS);
		verifyNoInteractions(auditLogger);
	}

	@Test
	@DisplayName("파이프라인에 닿지 못하면 502 IMPORT_UNAVAILABLE")
	void startImport_pipelineDown() {
		when(pipelineClient.createJob(any())).thenThrow(new PipelineException("닿지 못함"));

		assertThatThrownBy(() -> service.startImport(request(), admin, httpRequest))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.IMPORT_UNAVAILABLE);
	}

	@Test
	@DisplayName("서비스 주소가 없으면 등록만 막는다 (서버는 정상, 파이프라인을 부르지 않는다)")
	void startImport_whenDisabled() {
		when(pipelineClient.enabled()).thenReturn(false);

		assertThatThrownBy(() -> service.startImport(request(), admin, httpRequest))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.IMPORT_UNAVAILABLE);
		verify(pipelineClient, never()).createJob(any());
	}

	@Test
	@DisplayName("작업 행을 남기지 못하면 파이프라인에 남은 원문을 치운다 (FR-5-3)")
	void startImport_whenSaveFails_cleansUpPipelineJob() {
		when(pipelineClient.createJob(any())).thenReturn(job("abc123", "QUEUED", List.of()));
		when(importRepository.save(any())).thenThrow(new IllegalStateException("저장 실패"));

		assertThatThrownBy(() -> service.startImport(request(), admin, httpRequest))
			.isInstanceOf(IllegalStateException.class);

		verify(pipelineClient).deleteJobQuietly("abc123");
	}

	@Test
	@DisplayName("최종 확정 판결을 표시하면 그 번호를 파이프라인에 넘긴다")
	void startImport_passesFinalSourceIndex() {
		when(pipelineClient.createJob(any())).thenReturn(job("abc123", "QUEUED", List.of()));
		CaseImportCreateRequest request = new CaseImportCreateRequest(CrimeType.MURDER, List.of(
			new CaseImportCreateRequest.Source(CourtLevel.FIRST, "2099고합001", null, null, false, null, "1심", null),
			new CaseImportCreateRequest.Source(CourtLevel.APPEAL, "2099노001", null, null, true, null, "2심", null)));

		service.startImport(request, admin, httpRequest);

		ArgumentCaptor<PipelineJobRequest> sent = ArgumentCaptor.forClass(PipelineJobRequest.class);
		verify(pipelineClient).createJob(sent.capture());
		assertThat(sent.getValue().finalSourceIndex()).isEqualTo(1);
	}

	// ---------------------------------------------------------------- A-15

	@Test
	@DisplayName("상태를 물으면 파이프라인 단계를 그대로 옮긴다 (SKIPPED 포함)")
	void getImport_copiesStages() {
		CaseImport entity = running("abc123");
		when(importRepository.findById(1L)).thenReturn(Optional.of(entity));
		when(pipelineClient.getJob("abc123")).thenReturn(job("abc123", "RUNNING",
			List.of(stage("extract", "DONE"), stage("rag", "SKIPPED"))));

		CaseImportResponse response = service.getImport(1L);

		assertThat(response.status()).isEqualTo("RUNNING");
		assertThat(response.stages()).extracting(CaseImportResponse.Stage::status).containsExactly("DONE", "SKIPPED");
		assertThat(response.caseId()).isNull();	// 적재는 2차 PR
	}

	@Test
	@DisplayName("끝난 작업은 파이프라인에 다시 묻지 않고, 마지막으로 받은 단계를 그대로 보여준다")
	void getImport_finished_doesNotAskAgain() {
		CaseImport entity = running("abc123");
		entity.applyProgress(CaseImportStatus.SUCCEEDED, null, null, null,
			List.of(Map.of("name", "extract", "status", "DONE")));
		when(importRepository.findById(1L)).thenReturn(Optional.of(entity));

		CaseImportResponse response = service.getImport(1L);

		assertThat(response.status()).isEqualTo("SUCCEEDED");
		assertThat(response.stages()).singleElement()
			.satisfies(stage -> assertThat(stage.status()).isEqualTo("DONE"));
		verify(pipelineClient, never()).getJob(anyString());
	}

	@Test
	@DisplayName("파이프라인이 작업을 잃으면 실패로 끝낸다 (영원히 폴링하지 않게)")
	void getImport_jobGone_marksFailed() {
		CaseImport entity = running("abc123");
		when(importRepository.findById(1L)).thenReturn(Optional.of(entity));
		when(pipelineClient.getJob("abc123")).thenThrow(new PipelineException.NotFound("없음"));

		CaseImportResponse response = service.getImport(1L);

		assertThat(response.status()).isEqualTo("FAILED");
		assertThat(response.errorCode()).isEqualTo(ErrorCode.IMPORT_NOT_FOUND.name());
	}

	@Test
	@DisplayName("파이프라인이 일시적으로 안 되면 상태를 바꾸지 않는다 (잠깐 끊긴 것을 실패로 만들지 않는다)")
	void getImport_temporaryFailure_keepsStatus() {
		CaseImport entity = running("abc123");
		when(importRepository.findById(1L)).thenReturn(Optional.of(entity));
		when(pipelineClient.getJob("abc123")).thenThrow(new PipelineException("닿지 못함"));

		CaseImportResponse response = service.getImport(1L);

		assertThat(response.status()).isEqualTo("RUNNING");
		assertThat(response.errorCode()).isNull();
	}

	@Test
	@DisplayName("한 번 끝난 작업은 나중 조회로 뒤집히지 않는다 (서비스를 통해 확인)")
	void getImport_finishedStaysFinished() {
		CaseImport entity = running("abc123");
		when(importRepository.findById(1L)).thenReturn(Optional.of(entity));
		when(pipelineClient.getJob("abc123"))
			.thenReturn(job("abc123", "FAILED", List.of(stage("extract", "FAILED"))))
			.thenReturn(job("abc123", "RUNNING", List.of(stage("extract", "RUNNING"))));

		assertThat(service.getImport(1L).status()).isEqualTo("FAILED");
		// 끝난 작업은 다시 묻지 않으므로 두 번째 응답(RUNNING)은 쓰이지 않는다
		assertThat(service.getImport(1L).status()).isEqualTo("FAILED");
		verify(pipelineClient).getJob("abc123");
	}

	@Test
	@DisplayName("파이프라인 실패 사유가 컬럼 길이를 넘어도 저장할 수 있게 자른다 (안 자르면 조회가 계속 500)")
	void getImport_longErrorMessageIsTruncated() {
		CaseImport entity = running("abc123");
		when(importRepository.findById(1L)).thenReturn(Optional.of(entity));
		String tooLong = "비식별화 실패: " + "가".repeat(2000);
		when(pipelineClient.getJob("abc123")).thenReturn(new PipelineJob("abc123", "FAILED",
			List.of(stage("extract", "FAILED")), tooLong, null, null, null));

		CaseImportResponse response = service.getImport(1L);

		assertThat(response.status()).isEqualTo("FAILED");
		assertThat(response.message()).hasSizeLessThanOrEqualTo(500).endsWith("…");
	}

	@Test
	@DisplayName("처음부터 FAILED로 돌아온 작업도 실패 사유와 단계를 남긴다 (상태를 두 번 적지 않는다)")
	void startImport_jobAlreadyFailed_keepsReason() {
		when(pipelineClient.createJob(any())).thenReturn(new PipelineJob("abc123", "FAILED",
			List.of(stage("extract", "FAILED")), "비식별화 실패", null, null, null));
		ArgumentCaptor<CaseImport> saved = ArgumentCaptor.forClass(CaseImport.class);

		service.startImport(request(), admin, httpRequest);

		verify(importRepository).save(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(CaseImportStatus.FAILED);
		assertThat(saved.getValue().getMessage()).isEqualTo("비식별화 실패");
		assertThat(saved.getValue().getStages()).isNotEmpty();
		assertThat(saved.getValue().getFinishedAt()).isNotNull();
	}

	@Test
	@DisplayName("단계 값이 빠진 응답이 와도 500이 되지 않는다 (파이프라인 계약이 틀어져도 버틴다)")
	void getImport_stageWithoutStatus_doesNotFail() {
		CaseImport entity = running("abc123");
		when(importRepository.findById(1L)).thenReturn(Optional.of(entity));
		when(pipelineClient.getJob("abc123")).thenReturn(job("abc123", "RUNNING",
			java.util.Arrays.asList(new PipelineJob.Stage("extract", null, null), stage("court", "DONE"))));

		CaseImportResponse response = service.getImport(1L);

		assertThat(response.stages()).singleElement()
			.satisfies(stage -> assertThat(stage.name()).isEqualTo("court"));
	}

	@Test
	@DisplayName("적재 전이라도 진행 중인 등록이 쓴 사건번호면 409 — 같은 판결문을 또 돌리지 않는다")
	void startImport_duplicateAgainstRunningImport() {
		when(importRepository.findCaseNumbersInUse(any(), any())).thenReturn(List.of("2099고합001"));

		assertThatThrownBy(() -> service.startImport(request(), admin, httpRequest))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_SOURCE);
		verify(pipelineClient, never()).createJob(any());
	}

	@Test
	@DisplayName("없는 등록 작업은 404 IMPORT_NOT_FOUND")
	void getImport_notFound() {
		when(importRepository.findById(9L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getImport(9L))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.IMPORT_NOT_FOUND);
	}

	// ---------------------------------------------------------------- 도우미

	private static CaseImportCreateRequest request() {
		return new CaseImportCreateRequest(CrimeType.MURDER, List.of(source(CourtLevel.FIRST, "2099고합001")));
	}

	private static CaseImportCreateRequest.Source source(CourtLevel level, String caseNumber) {
		return source(level, caseNumber, true);
	}

	/** 최종 확정 판결은 한 건까지라, 두 건 이상 넣는 테스트는 하나만 true로 둔다 */
	private static CaseImportCreateRequest.Source source(CourtLevel level, String caseNumber, boolean isFinal) {
		return new CaseImportCreateRequest.Source(level, caseNumber, "가상지방법원", LocalDate.of(2025, 1, 10),
			isFinal, "법제처", "판결문 본문", null);
	}

	private static CaseImport running(String jobId) {
		CaseImport entity = CaseImport.start(jobId, CrimeType.MURDER);
		entity.applyProgress(CaseImportStatus.RUNNING, null, null, null, null);
		return entity;
	}

	private static PipelineJob job(String jobId, String status, List<PipelineJob.Stage> stages) {
		return new PipelineJob(jobId, status, stages, null, null, null, null);
	}

	private static PipelineJob.Stage stage(String name, String status) {
		return new PipelineJob.Stage(name, status, null);
	}

	/** TransactionTemplate을 쓰는 서비스를 단위 테스트하려고 둔다 (AdminCasePaperServiceTest와 같은 방식) */
	static class NoOpTransactionManager implements PlatformTransactionManager {

		@Override
		public TransactionStatus getTransaction(TransactionDefinition definition) {
			return new SimpleTransactionStatus();
		}

		@Override
		public void commit(TransactionStatus status) {
		}

		@Override
		public void rollback(TransactionStatus status) {
		}
	}
}
