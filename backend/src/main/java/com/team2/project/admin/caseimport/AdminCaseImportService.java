package com.team2.project.admin.caseimport;

import com.team2.project.admin.audit.AdminAuditAction;
import com.team2.project.admin.audit.AuditTargetType;
import com.team2.project.admin.audit.AdminAuditLogger;
import com.team2.project.admin.caseimport.domain.CaseImport;
import com.team2.project.admin.caseimport.domain.CaseImportSource;
import com.team2.project.admin.caseimport.domain.CaseImportStatus;
import com.team2.project.admin.caseimport.dto.CaseImportCreateRequest;
import com.team2.project.admin.caseimport.dto.CaseImportCreatedResponse;
import com.team2.project.admin.caseimport.dto.CaseImportResponse;
import com.team2.project.admin.caseimport.load.CaseImportLoader;
import com.team2.project.admin.caseimport.repository.CaseImportRepository;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.exception.FieldErrorDetail;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.repository.CaseSourceRepository;
import com.team2.project.pipeline.PipelineClient;
import com.team2.project.pipeline.PipelineException;
import com.team2.project.pipeline.PipelineJob;
import com.team2.project.pipeline.PipelineJobRequest;
import com.team2.project.pipeline.PipelineResult;
import com.team2.project.pipeline.config.PipelineProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사건 등록 (BE-93, API 명세 A-14 · A-15).
 *
 * <p>흐름: 관리자가 판결문을 넣으면 ① 검증 · 중복 확인 → ② 파이프라인 서비스(COMMON-50)에 작업을 맡기고
 * → ③ 작업 행을 남긴 뒤 202로 답한다. 진행 상황은 화면이 2초마다 A-15로 묻고, 그때마다 파이프라인에
 * 물어 받아 적는다.
 *
 * <p><b>진행 상황의 출처는 파이프라인의 state.json 하나다.</b> 백엔드가 따로 세지 않는다 — 두 군데서 세면
 * 반드시 어긋난다(COMMON-50과 같은 원칙). case_import 행은 그 결과를 받아 적는 쪽이고, 마지막으로 받은
 * 단계 상태를 그대로 저장해 둔다(화면을 새로 열어도 끝난 작업의 단계를 그대로 보여주기 위해서다).
 *
 * <p><b>1차 PR 범위</b>: 적재(파이프라인 결과 → legal_case DRAFT)는 2차 PR에서 한다. 그래서 지금은
 * SUCCEEDED가 되어도 caseId가 null이다. 미완성이 아니라 의도한 경계다.
 */
@Slf4j
@Service
public class AdminCaseImportService {

	private final CaseImportRepository importRepository;
	private final CaseSourceRepository caseSourceRepository;
	private final PipelineClient pipelineClient;
	private final PipelineProperties pipelineProperties;
	private final AdminAuditLogger auditLogger;
	private final CaseImportLoader loader;
	private final TransactionTemplate transactionTemplate;

	/** 파이프라인 단계 순서 (COMMON-50 STAGES와 같아야 한다). 아직 아무 값도 못 받았을 때 쓴다 */
	private static final List<String> STAGES =
		List.of("extract", "court", "contamination", "rag", "generate", "select", "axis", "load");

	private static final String STAGE_PENDING = "PENDING";

	/**
	 * 명시적 생성자를 쓴다 (@RequiredArgsConstructor 대신): 외부 호출을 트랜잭션 밖에 두고 저장만 짧게
	 * 묶으려면 TransactionTemplate이 필요하기 때문이다 (AdminCasePaperService와 같은 구조).
	 */
	public AdminCaseImportService(CaseImportRepository importRepository, CaseSourceRepository caseSourceRepository,
			PipelineClient pipelineClient, PipelineProperties pipelineProperties, AdminAuditLogger auditLogger,
			CaseImportLoader loader, PlatformTransactionManager transactionManager) {
		this.importRepository = importRepository;
		this.caseSourceRepository = caseSourceRepository;
		this.pipelineClient = pipelineClient;
		this.pipelineProperties = pipelineProperties;
		this.auditLogger = auditLogger;
		this.loader = loader;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/** A-14. 작업을 만들고 바로 시작한다 */
	public CaseImportCreatedResponse startImport(CaseImportCreateRequest request, AdminPrincipal admin,
			HttpServletRequest httpRequest) {
		if (!pipelineClient.enabled()) {
			// 파이프라인 오버레이를 켜지 않은 서버다. 등록만 막고 나머지 기능은 그대로 쓴다
			throw new BusinessException(ErrorCode.IMPORT_UNAVAILABLE,
				"사건 등록 서비스가 연결되지 않았습니다 (관리자에게 문의해 주세요).");
		}
		guardDistinctCourtLevels(request.sources());
		guardSingleFinalDecision(request.sources());
		guardNotAlreadyImported(request.sources());
		guardNoRunningImport();

		// 파이프라인 호출은 트랜잭션 밖에서 한다 — 외부 호출 동안 DB 커넥션을 잡고 있지 않게 (BE-92와 같은 이유)
		PipelineJob job = createJob(request);
		try {
			return transactionTemplate.execute(status -> {
				CaseImport saved = importRepository.save(toEntity(request, job));
				// 감사 로그는 같은 트랜잭션에 묶는다 — 작업 행과 기록이 함께 남거나 함께 없어야 한다
				auditLogger.record(admin, AdminAuditAction.CASE_IMPORT, null, null, auditDetail(request), httpRequest);
				return CaseImportCreatedResponse.from(saved);
			});
		} catch (RuntimeException e) {
			// 작업 행을 남기지 못하면 추적할 길이 없다. 파이프라인에 남은 원문부터 치운다 (FR-5-3)
			pipelineClient.deleteJobQuietly(job.jobId());
			throw e;
		}
	}

	/**
	 * A-15. 파이프라인에 물어 받아 적고 그대로 내려준다.
	 *
	 * <p><b>파이프라인 호출은 트랜잭션 밖에서 한다.</b> 화면이 2초마다 부르는 경로라, 트랜잭션 안에서
	 * 부르면 파이프라인이 멎었을 때 호출 하나하나가 DB 커넥션을 타임아웃(30초)까지 붙잡는다 —
	 * 탭 몇 개만 열려 있어도 커넥션 풀이 말라 <b>서비스 전체가 멈춘다</b> (PR 리뷰 지적).
	 * 읽기 · 쓰기만 짧은 트랜잭션으로 묶는다.
	 */
	public CaseImportResponse getImport(Long importId) {
		CaseImport entity = read(importId);
		if (entity.getStatus().isFinished()) {
			// 끝난 작업은 다시 묻지 않는다. 화면이 2초마다 부르므로 불필요한 호출을 줄인다
			return CaseImportResponse.of(entity, lastKnownStages(entity));
		}
		PipelineJob job;
		try {
			job = pipelineClient.getJob(entity.getJobId());
		} catch (PipelineException.NotFound e) {
			// 서비스가 작업을 잃었다 (컨테이너를 볼륨째 지웠을 때 등). 되살릴 길이 없으므로 실패로 끝낸다
			return record(importId, CaseImportStatus.FAILED, ErrorCode.IMPORT_NOT_FOUND.name(),
				"등록 작업이 사라졌습니다. 다시 등록해 주세요.", Instant.now(), null);
		} catch (PipelineException e) {
			// 일시적인 장애일 수 있다. 상태를 바꾸지 않고 지금까지 아는 값을 그대로 돌려준다
			log.warn("등록 상태를 가져오지 못했습니다 (importId={}): {}", importId, e.getMessage());
			return CaseImportResponse.of(entity, lastKnownStages(entity));
		}
		if (job == null) {
			return CaseImportResponse.of(entity, lastKnownStages(entity));
		}
		return record(importId, statusOf(job), errorCodeOf(job), job.error(), parseInstant(job.finishedAt()),
			stageSnapshot(job));
	}

	private CaseImport read(Long importId) {
		return transactionTemplate.execute(ignored -> importRepository.findById(importId)
			.orElseThrow(() -> new BusinessException(ErrorCode.IMPORT_NOT_FOUND)));
	}

	/** 알아낸 진행 상황을 짧은 트랜잭션으로 받아 적고, 그 결과로 응답을 만든다 (A-15) */
	private CaseImportResponse record(Long importId, CaseImportStatus status, String errorCode, String message,
			Instant finishedAt, List<Map<String, String>> stages) {
		return transactionTemplate.execute(ignored -> {
			CaseImport managed = importRepository.findById(importId)
				.orElseThrow(() -> new BusinessException(ErrorCode.IMPORT_NOT_FOUND));
			managed.applyProgress(status, errorCode, message, finishedAt, stages);
			return CaseImportResponse.of(managed, lastKnownStages(managed));
		});
	}

	/**
	 * 같은 일을 하되 행이 없어도 넘어간다 (A-14 가드 경로).
	 *
	 * <p>등록을 시작하려다 남의 작업 상태를 갱신하는 중이라, 그 사이 누가 행을 지웠다고 해서 <b>새 등록
	 * 요청이 404로 실패하면 안 된다</b> — 행이 없으면 "도는 작업이 없다"는 뜻이므로 그대로 진행한다.
	 */
	private void recordQuietly(Long importId, CaseImportStatus status, String errorCode, String message,
			Instant finishedAt, List<Map<String, String>> stages) {
		transactionTemplate.executeWithoutResult(ignored -> importRepository.findById(importId)
			.ifPresent(managed -> managed.applyProgress(status, errorCode, message, finishedAt, stages)));
	}

	/**
	 * A-14-1. 생성 결과를 사건으로 적재한다 (BE-93 2차).
	 *
	 * <p>관리자가 A-15에서 `SUCCEEDED`를 보고 누른다. <b>조회(A-15)가 아니라 별도 호출로 둔 이유</b>:
	 * A-15는 2초마다 불리는 조회라 거기서 적재하면 같은 사건이 여러 번 만들어질 위험이 있다.
	 *
	 * <p>파이프라인 호출은 트랜잭션 밖에서 하고, 쓰기만 {@link CaseImportLoader}가 한 트랜잭션으로 묶는다.
	 */
	public CaseImportResponse loadImport(Long importId, AdminPrincipal admin, HttpServletRequest httpRequest) {
		CaseImport entity = read(importId);
		if (entity.isLoaded()) {
			throw new BusinessException(ErrorCode.IMPORT_NOT_READY, "이미 사건으로 만든 등록입니다.");
		}
		if (entity.getStatus() != CaseImportStatus.SUCCEEDED) {
			throw new BusinessException(ErrorCode.IMPORT_NOT_READY,
				"아직 생성이 끝나지 않았습니다. 진행 상황을 먼저 확인해 주세요.");
		}
		PipelineResult result = fetchResult(entity.getJobId());

		LegalCase legalCase = transactionTemplate.execute(status -> {
			CaseImport managed = importRepository.findById(importId)
				.orElseThrow(() -> new BusinessException(ErrorCode.IMPORT_NOT_FOUND));
			LegalCase created = loader.load(managed, result);
			managed.linkLoadedCase(created);
			// 원문은 적재하면서 case_source로 옮겼다. 사건번호만 남겨 무엇이 만들어졌는지 추적한다
			auditLogger.record(admin, AdminAuditAction.CASE_IMPORT_LOAD, AuditTargetType.CASE, created.getId(),
				Map.of("importId", importId), httpRequest);
			return created;
		});
		log.info("사건 등록 적재 완료: importId={}, caseId={}", importId, legalCase.getId());
		// 적재가 끝났으니 파이프라인에 남은 판결문 원문을 치운다 (FR-5-3)
		pipelineClient.deleteJobQuietly(entity.getJobId());
		return getImport(importId);
	}

	private PipelineResult fetchResult(String jobId) {
		PipelineResult result;
		try {
			result = pipelineClient.getResult(jobId);
		} catch (PipelineException.NotFound e) {
			throw new BusinessException(ErrorCode.IMPORT_NOT_READY,
				"생성 결과가 사라졌습니다. 다시 등록해 주세요.");
		} catch (PipelineException e) {
			log.warn("생성 결과를 가져오지 못했습니다: {}", e.getMessage());
			throw new BusinessException(ErrorCode.IMPORT_UNAVAILABLE, "생성 결과를 가져오지 못했습니다.");
		}
		if (!result.loadable()) {
			throw new BusinessException(ErrorCode.IMPORT_RESULT_INVALID,
				"생성 결과에 사건 또는 AI 판결이 없어 사건을 만들 수 없습니다.");
		}
		return result;
	}

	// ---------------------------------------------------------------- 검증

	/** 같은 심급을 두 번 넣을 수 없다 (API 명세 A-14). 1 ~ 3건 제한은 애너테이션이 본다 */
	private static void guardDistinctCourtLevels(List<CaseImportCreateRequest.Source> sources) {
		Set<?> levels = new HashSet<>(sources.stream().map(CaseImportCreateRequest.Source::courtLevel).toList());
		if (levels.size() != sources.size()) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "sources");
		}
	}

	/**
	 * 최종 확정 판결은 1건까지 (PR #232 CodeRabbit 리뷰).
	 *
	 * <p>여럿을 표시해도 파이프라인에는 <b>첫 번째 번호만</b> 넘어가므로, 파이프라인이 보는 최종 판결과
	 * 저장해 둔 원본 정보가 어긋난다. 게다가 `case_source`에는 "사건마다 최종 판결 1건" 유니크 제약이
	 * 있어(V6), 그대로 두면 <b>수십 분짜리 파이프라인이 끝난 뒤 적재에서야</b> 실패한다.
	 * 받는 자리에서 막는 편이 훨씬 싸다.
	 */
	private static void guardSingleFinalDecision(List<CaseImportCreateRequest.Source> sources) {
		if (sources.stream().filter(CaseImportCreateRequest.Source::finalDecision).count() > 1) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "sources");
		}
	}

	/**
	 * 이미 등록한 사건번호면 409 (API 명세 A-14 DUPLICATE_SOURCE).
	 *
	 * <p>적재된 사건(`case_source`)과 <b>등록 요청(`case_import_source`)을 함께 본다.</b> 적재가 들어가기
	 * 전(2차 전)에는 `case_source`가 비어 있어, 적재된 사건만 보면 같은 판결문으로 수십 분짜리 생성을
	 * 몇 번이고 다시 돌리게 된다 (PR 리뷰 지적).
	 */
	private void guardNotAlreadyImported(List<CaseImportCreateRequest.Source> sources) {
		List<String> caseNumbers = sources.stream().map(CaseImportCreateRequest.Source::caseNumber).toList();
		List<String> existing = new ArrayList<>(caseSourceRepository.findExistingCaseNumbers(caseNumbers));
		existing.addAll(importRepository.findCaseNumbersInUse(caseNumbers, CaseImportStatus.FAILED));
		if (existing.isEmpty()) {
			return;
		}
		List<FieldErrorDetail> details = new ArrayList<>();
		for (int i = 0; i < caseNumbers.size(); i++) {
			if (existing.contains(caseNumbers.get(i))) {
				details.add(new FieldErrorDetail("sources[" + i + "].caseNumber", ErrorCode.DUPLICATE_SOURCE.name()));
			}
		}
		throw new BusinessException(ErrorCode.DUPLICATE_SOURCE, ErrorCode.DUPLICATE_SOURCE.getMessage(), null, details);
	}

	/**
	 * 한 번에 하나만 등록한다 (API 명세 A-14 IMPORT_IN_PROGRESS).
	 *
	 * <p>여기서 막는 것은 빠른 피드백용이다. <b>진짜 경계는 파이프라인 서비스가 지킨다</b> — 서비스가
	 * 확인과 시작을 잠금으로 묶어 두어(COMMON-50), 이 검사를 동시에 통과해도 둘째 요청은 409로 거절된다.
	 *
	 * <p><b>막기 전에 파이프라인에 한 번 확인한다.</b> 상태는 A-15를 물을 때만 갱신되는데, 관리자가 화면을
	 * 닫으면 아무도 묻지 않는다. 그러면 이미 끝난 등록이 DB에는 RUNNING으로 남아 <b>이후 모든 등록을
	 * 영원히 막는다</b>. 도는 작업은 하나뿐이라 확인도 한 번이다.
	 */
	private void guardNoRunningImport() {
		List<CaseImport> running =
			importRepository.findByStatusIn(List.of(CaseImportStatus.QUEUED, CaseImportStatus.RUNNING));
		for (CaseImport entity : running) {
			if (stillRunning(entity)) {
				throw new BusinessException(ErrorCode.IMPORT_IN_PROGRESS);
			}
		}
	}

	/** 파이프라인에 물어 받아 적고, 아직 도는 중인지 알려준다. 확인할 수 없으면 막는 쪽(true)을 고른다 */
	private boolean stillRunning(CaseImport entity) {
		PipelineJob job;
		try {
			job = pipelineClient.getJob(entity.getJobId());
		} catch (PipelineException.NotFound e) {
			// 작업이 사라졌다. 되살릴 수 없으니 실패로 끝내고 다음 등록의 길을 비운다
			recordQuietly(entity.getId(), CaseImportStatus.FAILED, ErrorCode.IMPORT_NOT_FOUND.name(),
				"등록 작업이 사라졌습니다. 다시 등록해 주세요.", Instant.now(), null);
			return false;
		} catch (PipelineException e) {
			// 지금 확인할 수 없다. 도는 작업을 두고 또 시작하는 것보다 기다리게 하는 쪽이 안전하다
			log.warn("진행 중인 등록을 확인하지 못했습니다 (importId={}): {}", entity.getId(), e.getMessage());
			return true;
		}
		if (job == null) {
			return true;	// 알 수 없으면 막는 쪽
		}
		CaseImportStatus next = statusOf(job);
		recordQuietly(entity.getId(), next, errorCodeOf(job), job.error(), parseInstant(job.finishedAt()),
			stageSnapshot(job));
		return !next.isFinished();
	}

	// ---------------------------------------------------------------- 파이프라인

	private PipelineJob createJob(CaseImportCreateRequest request) {
		try {
			return pipelineClient.createJob(toPipelineRequest(request));
		} catch (PipelineException.Conflict e) {
			// 서비스 쪽 잠금에 걸렸다 = 다른 등록이 도는 중
			throw new BusinessException(ErrorCode.IMPORT_IN_PROGRESS);
		} catch (PipelineException e) {
			log.warn("파이프라인 작업을 만들지 못했습니다: {}", e.getMessage());
			throw new BusinessException(ErrorCode.IMPORT_UNAVAILABLE, "사건 등록을 시작하지 못했습니다.");
		}
	}

	private PipelineJobRequest toPipelineRequest(CaseImportCreateRequest request) {
		List<PipelineJobRequest.Source> sources = request.sources().stream()
			.map(source -> new PipelineJobRequest.Source(source.originalText(), source.note()))
			.toList();
		Map<String, Object> stages = null;
		if (pipelineProperties.extractModel() != null && !pipelineProperties.extractModel().isBlank()) {
			stages = Map.of("extract", Map.of("model", pipelineProperties.extractModel()));
		}
		return new PipelineJobRequest(sources, pipelineProperties.models(), stages, finalSourceIndex(request));
	}

	/** 최종 확정 판결의 번호(0부터). 아무도 표시하지 않았으면 null — 파이프라인이 심급으로 정한다 */
	private static Integer finalSourceIndex(CaseImportCreateRequest request) {
		List<CaseImportCreateRequest.Source> sources = request.sources();
		for (int i = 0; i < sources.size(); i++) {
			if (sources.get(i).finalDecision()) {
				return i;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- 저장 · 변환

	private static CaseImport toEntity(CaseImportCreateRequest request, PipelineJob job) {
		CaseImport entity = CaseImport.start(job.jobId(), request.crimeType());
		// 상태는 한 번만 적는다. 파이프라인이 처음부터 FAILED를 주는 경우도 있어 실패 사유까지 함께 받아 적는다
		entity.applyProgress(statusOf(job), errorCodeOf(job), job.error(), parseInstant(job.finishedAt()),
			stageSnapshot(job));
		for (CaseImportCreateRequest.Source source : request.sources()) {
			entity.addSource(CaseImportSource.of(source.courtLevel(), source.caseNumber(), source.courtName(),
				source.decidedAt(), source.finalDecision(), source.sourceOrg(), source.originalText(), source.note()));
		}
		return entity;
	}

	/** 감사 로그에 남길 값. <b>원문은 넣지 않는다</b> — 사건번호 · 심급만 (API 명세 A-14) */
	private static Map<String, Object> auditDetail(CaseImportCreateRequest request) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("crimeType", request.crimeType().name());
		detail.put("sources", request.sources().stream()
			.map(source -> Map.of("courtLevel", source.courtLevel().name(), "caseNumber", source.caseNumber()))
			.toList());
		return detail;
	}

	private static CaseImportStatus statusOf(PipelineJob job) {
		try {
			return CaseImportStatus.valueOf(job.status());
		} catch (IllegalArgumentException | NullPointerException e) {
			log.warn("파이프라인이 모르는 상태를 보냈습니다: {}", job.status());
			return CaseImportStatus.RUNNING;
		}
	}

	private static String errorCodeOf(PipelineJob job) {
		return CaseImportStatus.FAILED.name().equals(job.status()) ? ErrorCode.INTERNAL_ERROR.name() : null;
	}

	/** 파이프라인이 준 단계 상태를 저장할 모양으로 바꾼다. 못 받았으면 null (이전 값을 그대로 둔다) */
	private static List<Map<String, String>> stageSnapshot(PipelineJob job) {
		if (job.stages() == null || job.stages().isEmpty()) {
			return null;
		}
		// Map.of는 null을 거부한다. 파이프라인이 값 하나를 빠뜨리면 조회가 500이 되므로 빈 것은 건너뛴다
		return job.stages().stream()
			.filter(stage -> stage != null && stage.name() != null && stage.status() != null)
			.map(stage -> Map.of("name", stage.name(), "status", stage.status()))
			.toList();
	}

	/**
	 * 마지막으로 받은 단계 상태. 아직 한 번도 못 받았으면 모두 PENDING으로 둔다.
	 *
	 * <p>단계별 상태는 파이프라인만 알고 있어서 지어내지 않는다 — 받아 둔 값이 없으면 "아직 모름"에
	 * 해당하는 PENDING을 쓴다.
	 */
	private static List<CaseImportResponse.Stage> lastKnownStages(CaseImport entity) {
		List<Map<String, String>> stored = entity.getStages();
		if (stored == null || stored.isEmpty()) {
			return STAGES.stream().map(name -> new CaseImportResponse.Stage(name, STAGE_PENDING)).toList();
		}
		return stored.stream()
			.map(stage -> new CaseImportResponse.Stage(stage.get("name"), stage.get("status")))
			.toList();
	}

	private static Instant parseInstant(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(value).toInstant();
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
