package com.team2.project.admin.review;

import com.team2.project.admin.audit.AdminAuditAction;
import com.team2.project.admin.audit.AdminAuditLogger;
import com.team2.project.admin.audit.AuditTargetType;
import com.team2.project.admin.review.dto.CaseScriptResponse;
import com.team2.project.admin.review.dto.ScriptRegenerationRequest;
import com.team2.project.admin.review.dto.ScriptRegenerationResponse;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.SubjectType;
import com.team2.project.judgment.domain.Timing;
import com.team2.project.judgment.repository.JudgmentFactorRepository;
import com.team2.project.judgment.repository.JudgmentRepository;
import com.team2.project.legalcase.domain.CasePaper;
import com.team2.project.legalcase.domain.CaseScript;
import com.team2.project.legalcase.domain.CaseScript.RegeneratedBlock;
import com.team2.project.legalcase.domain.CaseScriptBlock;
import com.team2.project.legalcase.domain.CaseScriptRegeneration;
import com.team2.project.legalcase.domain.CastRoles;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.ScriptRegenerationStatus;
import com.team2.project.legalcase.domain.ScriptUnit;
import com.team2.project.legalcase.repository.CasePaperRepository;
import com.team2.project.legalcase.repository.CaseScriptRegenerationRepository;
import com.team2.project.legalcase.repository.CaseScriptRepository;
import com.team2.project.legalcase.repository.CaseSectionRepository;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import com.team2.project.pipeline.PipelineClient;
import com.team2.project.pipeline.PipelineException;
import com.team2.project.pipeline.PipelineJob;
import com.team2.project.pipeline.PipelineRegenerationRequest;
import com.team2.project.pipeline.config.PipelineProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 대본 블록 재생성 (BE-129 2차, API 명세 A-13-15 ~ 17). 관리자가 고른 블록을 파이프라인이 다시 만들고(작업 생성), 화면이 진행 상황을
 * 묻고(상태 · 미리보기), 끝나면 관리자가 보고 반영한다 — 사건 등록(A-14 → A-15 → A-14-1)과 같은 흐름이다.
 *
 * <p><b>자동으로 반영하지 않는다.</b> 다시 만든 대사는 새 모델 출력이고, U3를 다시 만들면 관리자가 직접 고친 줄(A-13-13)이 바뀐다
 * (PR #378 리뷰 3번). 관리자가 미리보기로 확인하고 반영한다. 반영하면 그 블록은 다시 검수 대기다.
 *
 * <p><b>파이프라인 호출은 트랜잭션 밖에서 한다</b>(사건 등록과 같은 이유 — 외부 호출 동안 DB 커넥션을 잡지 않는다). DB 읽기 · 쓰기만
 * 짧은 트랜잭션으로 묶는다.
 *
 * <p><b>반영할 때 입력이 그대로인지 본다.</b> 요청한 뒤 대사를 고치거나 다른 재생성을 반영했거나, 사건 내용 · 기사 · 배역 · 분류 ·
 * 민감 플래그를 바꿨으면 옛 입력으로 만든 블록이라 거절하고 다시 요청하게 한다({@link ScriptRegenerationInputs#snapshot}).
 */
@Slf4j
@Service
public class AdminScriptRegenerationService {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String STAGE_SKIPPED = "SKIPPED";

	static final String NOT_LATEST = "이 사건에 더 최근 재생성 요청이 있습니다. 최근 요청의 결과를 반영해 주세요.";
	static final String INPUTS_CHANGED = "재생성을 요청한 뒤 대본 · 사건 내용 · 기사 · 배역 · 분류가 바뀌었습니다. 다시 요청해 주세요.";

	private final LegalCaseRepository caseRepository;
	private final CaseScriptRepository scriptRepository;
	private final CaseScriptRegenerationRepository regenerationRepository;
	private final CasePaperRepository paperRepository;
	private final CaseSectionRepository sectionRepository;
	private final FactorRepository factorRepository;
	private final JudgmentRepository judgmentRepository;
	private final JudgmentFactorRepository judgmentFactorRepository;
	private final PipelineClient pipelineClient;
	private final PipelineProperties pipelineProperties;
	private final AdminAuditLogger auditLogger;
	private final AdminCaseScriptService scriptService;
	private final TransactionTemplate transactionTemplate;

	public AdminScriptRegenerationService(LegalCaseRepository caseRepository, CaseScriptRepository scriptRepository,
			CaseScriptRegenerationRepository regenerationRepository, CasePaperRepository paperRepository,
			CaseSectionRepository sectionRepository, FactorRepository factorRepository,
			JudgmentRepository judgmentRepository, JudgmentFactorRepository judgmentFactorRepository,
			PipelineClient pipelineClient, PipelineProperties pipelineProperties, AdminAuditLogger auditLogger,
			AdminCaseScriptService scriptService, PlatformTransactionManager transactionManager) {
		this.caseRepository = caseRepository;
		this.scriptRepository = scriptRepository;
		this.regenerationRepository = regenerationRepository;
		this.paperRepository = paperRepository;
		this.sectionRepository = sectionRepository;
		this.factorRepository = factorRepository;
		this.judgmentRepository = judgmentRepository;
		this.judgmentFactorRepository = judgmentFactorRepository;
		this.pipelineClient = pipelineClient;
		this.pipelineProperties = pipelineProperties;
		this.auditLogger = auditLogger;
		this.scriptService = scriptService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/** 요청할 때 모은 것: 서비스에 보낼 입력과 반영할 때 비교할 입력 기록 */
	private record Prepared(Map<String, Object> request, Map<String, Object> snapshot) {
	}

	// ---------------------------------------------------------------- A-13-15 작업 생성

	public ScriptRegenerationResponse start(Long caseId, ScriptRegenerationRequest body, AdminPrincipal admin,
			HttpServletRequest httpRequest) {
		List<String> units = units(body.units());
		boolean cascade = body.cascadeOrDefault();
		if (!pipelineClient.enabled() || !pipelineProperties.scriptEnabled()) {
			throw new BusinessException(ErrorCode.IMPORT_UNAVAILABLE,
				"대본 생성 서비스가 연결되지 않았습니다 (관리자에게 문의해 주세요).");
		}
		guardNoRunningRegeneration(caseId);
		Prepared prepared = transactionTemplate.execute(status -> prepare(caseId, units, cascade));
		PipelineJob job = createJob(prepared.request());
		try {
			return transactionTemplate.execute(status -> {
				LegalCase legalCase = caseRepository.findById(caseId)
					.orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));
				CaseScriptRegeneration saved = regenerationRepository.save(CaseScriptRegeneration.start(legalCase,
					job.jobId(), units, cascade, prepared.snapshot(), admin.adminId()));
				saved.applyProgress(statusOf(job), errorCodeOf(job), job.error(), parseInstant(job.finishedAt()),
					stageSnapshot(job));
				Map<String, Object> detail = new LinkedHashMap<>();
				detail.put("regenerationId", saved.getId());
				detail.put("units", units);
				detail.put("cascade", cascade);
				auditLogger.record(admin, AdminAuditAction.CASE_SCRIPT_REGENERATE, AuditTargetType.CASE, caseId, detail,
					httpRequest);
				return ScriptRegenerationResponse.of(saved, false, null, null);
			});
		} catch (RuntimeException e) {
			// 작업 행을 남기지 못하면 추적할 길이 없다. 파이프라인에 남은 사건 입력부터 치운다
			pipelineClient.deleteJobQuietly(job.jobId());
			throw e;
		}
	}

	/** 고른 블록 검사 — 아는 단위 이름, 겹치지 않게. 순서는 U0 → U4로 맞춘다 */
	private static List<String> units(List<String> requested) {
		List<String> units = new ArrayList<>();
		for (String name : requested) {
			if (name == null || ScriptUnit.find(name).isEmpty() || units.contains(name)) {
				throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "units");
			}
			units.add(name);
		}
		units.sort(Comparator.comparing(name -> ScriptUnit.valueOf(name).ordinal()));
		return units;
	}

	/**
	 * 같은 사건의 재생성이 아직 돌면 막는다. 상태는 화면이 물을 때만 갱신되므로, 막기 전에 파이프라인에 한 번 확인한다(등록
	 * {@code guardNoRunningImport}와 같은 이유 — 화면을 닫으면 끝난 작업이 RUNNING으로 남아 이후 요청을 영원히 막는다).
	 * 다른 사건의 등록 · 재생성은 서비스가 409로 막는다(한 번에 하나).
	 */
	private void guardNoRunningRegeneration(Long caseId) {
		CaseScriptRegeneration latest = transactionTemplate.execute(status ->
			regenerationRepository.findFirstByLegalCaseIdOrderByIdDesc(caseId).orElse(null));
		if (latest != null && latest.isActive() && refresh(latest).isActive()) {
			throw new BusinessException(ErrorCode.IMPORT_IN_PROGRESS, "이 사건의 대본 재생성이 아직 도는 중입니다.");
		}
	}

	/**
	 * DB 값으로 입력을 모은다. 막는 조건: 대본이 없다 · 분류나 민감 플래그가 없다(미분류 — 민감 플래그 없이 다시 만들면 가드가
	 * 빠진다, PR #370 리뷰) · 배역이 없다 · AI 판결이 없다 · 1막 해설이 있거나 다시 만드는데 기사가 없다(검수가 U1을 기사와 비교한다,
	 * PR #378 리뷰)
	 */
	private Prepared prepare(Long caseId, List<String> units, boolean cascade) {
		LegalCase legalCase = caseRepository.findById(caseId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));
		CaseScript script = scriptRepository.findByLegalCaseId(caseId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
		if (legalCase.getClassification() == null || legalCase.getSensitiveFlags() == null) {
			throw new BusinessException(ErrorCode.INVALID_STATE,
				"분류나 민감 플래그가 없는 사건은 대본을 다시 만들 수 없습니다. 사건 수정(A-11)으로 먼저 정해 주세요.");
		}
		if (legalCase.getCastRoles() == null || CastRoles.parse(legalCase.getCastRoles()).isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_STATE,
				"배역이 없는 사건은 대본을 다시 만들 수 없습니다. 사건 수정(A-11)으로 먼저 정해 주세요.");
		}
		Judgment judgment = aiJudgment(caseId);
		CasePaper paper = paperRepository.findByLegalCaseId(caseId).orElse(null);
		boolean needsPaper = units.contains(ScriptUnit.U1.name()) || script.block(ScriptUnit.U1).isPresent();
		if (needsPaper && paper == null) {
			throw new BusinessException(ErrorCode.INVALID_STATE,
				"신문 기사가 없어 1막 해설을 다시 만들거나 검사할 수 없습니다. 기사(A-13-6)를 먼저 만들어 주세요.");
		}
		Map<String, Object> request = ScriptRegenerationInputs.request(legalCase, script, paper,
			sectionRepository.findAllByCaseId(caseId), factorRepository.findAllByCaseId(caseId), judgment,
			judgmentFactorRepository.findAllByJudgmentIds(List.of(judgment.getId())), units, cascade);
		Integer paperRevision = paper == null ? null : paper.getRevision();
		return new Prepared(request, ScriptRegenerationInputs.snapshot(legalCase, script, paperRevision));
	}

	/**
	 * 대본이 따를 AI 판결: 공개된 AI 판결, 없으면 적재 때 들어온(가장 먼저 만든) AI 판결이다. 대본은 처음에 적재된 판결의 방향으로
	 * 만들어졌고, 관리자가 다른 후보를 공개했으면 그 판결을 따르는 것이 맞다
	 */
	private Judgment aiJudgment(Long caseId) {
		return judgmentRepository.findPublishedJudgment(caseId, SubjectType.AI)
			.or(() -> judgmentRepository.findCandidatesByCaseId(caseId).stream()
				.filter(judgment -> judgment.getSubjectType() == SubjectType.AI && judgment.getTiming() == Timing.FINAL)
				.reduce((newer, older) -> older))		// 최신 순이라 마지막이 가장 먼저 만든 판결이다
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE,
				"AI 판결이 없는 사건은 대본을 다시 만들 수 없습니다."));
	}

	private PipelineJob createJob(Map<String, Object> request) {
		try {
			return pipelineClient.createRegenerationJob(new PipelineRegenerationRequest(request,
				Map.of("script", Map.of("model", pipelineProperties.scriptModel()))));
		} catch (PipelineException.Conflict e) {
			throw new BusinessException(ErrorCode.IMPORT_IN_PROGRESS, "다른 사건 등록이나 대본 재생성이 도는 중입니다.");
		} catch (PipelineException e) {
			log.warn("대본 재생성 작업을 만들지 못했습니다: {}", e.getMessage());
			throw new BusinessException(ErrorCode.IMPORT_UNAVAILABLE, "대본 재생성을 시작하지 못했습니다.");
		}
	}

	// ---------------------------------------------------------------- A-13-16 상태 · 미리보기

	public ScriptRegenerationResponse get(Long caseId, Long regenerationId) {
		CaseScriptRegeneration entity = read(caseId, regenerationId);
		if (entity.isActive()) {
			entity = refresh(entity);
		}
		JsonNode result = null;
		if (entity.getStatus() == ScriptRegenerationStatus.SUCCEEDED && !entity.isApplied()) {
			try {
				result = pipelineClient.getRegenerationResult(entity.getJobId());
			} catch (PipelineException e) {
				// 결과를 못 가져와도 상태는 보여 준다. 반영(A-13-17)이 다시 가져오며 사유를 알린다
				log.warn("재생성 결과를 가져오지 못했습니다 (regenerationId={}): {}", regenerationId, e.getMessage());
			}
		}
		JsonNode fetched = result;
		return transactionTemplate.execute(status -> view(caseId, regenerationId, fetched));
	}

	private ScriptRegenerationResponse view(Long caseId, Long regenerationId, JsonNode result) {
		CaseScriptRegeneration entity = regenerationRepository.findByIdAndLegalCaseId(regenerationId, caseId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_REGENERATION_NOT_FOUND));
		if (entity.isActive()) {
			return ScriptRegenerationResponse.of(entity, false, null, null);
		}
		CaseScript script = scriptRepository.findByLegalCaseId(caseId).orElse(null);
		String reason = notApplicable(entity, script, result);
		ScriptRegenerationResponse.Preview preview = result == null || entity.isApplied() ? null : preview(result, script);
		return ScriptRegenerationResponse.of(entity, reason == null, reason, preview);
	}

	/** 반영할 수 없는 이유. 반영할 수 있으면 null */
	private String notApplicable(CaseScriptRegeneration entity, CaseScript script, JsonNode result) {
		if (entity.getStatus() != ScriptRegenerationStatus.SUCCEEDED) {
			return "실패한 재생성입니다. 다시 요청해 주세요.";
		}
		if (entity.isApplied()) {
			return "이미 반영했습니다.";
		}
		Long latestId = regenerationRepository.findFirstByLegalCaseIdOrderByIdDesc(entity.getLegalCase().getId())
			.map(CaseScriptRegeneration::getId).orElse(null);
		if (!entity.getId().equals(latestId)) {
			return NOT_LATEST;
		}
		if (script == null || !ScriptRegenerationInputs.sameInputs(entity.getInputs(), currentSnapshot(script))) {
			return INPUTS_CHANGED;
		}
		if (result != null && texts(result.get("regenerated")).isEmpty()) {
			return "다시 만든 블록이 없습니다. 다시 요청해 주세요.";
		}
		return null;
	}

	private ScriptRegenerationResponse.Preview preview(JsonNode result, CaseScript script) {
		List<String> regenerated = texts(result.get("regenerated"));
		List<ScriptRegenerationResponse.Block> blocks = new ArrayList<>();
		for (String unit : regenerated) {
			JsonNode block = result.path("blocks").get(unit);
			if (block != null && block.isObject()) {
				blocks.add(new ScriptRegenerationResponse.Block(unit, plain(block.get("content")),
					block.path("revision").asInt(1), map(block.get("basis")), maps(block.get("review")),
					objects(block.get("warnings")), maps(block.get("repairs"))));
			}
		}
		long manual = regenerated.contains(ScriptUnit.U3.name()) && script != null
			? script.block(ScriptUnit.U3).map(CaseScriptBlock::manualLineCount).orElse(0L) : 0L;
		return new ScriptRegenerationResponse.Preview(texts(result.get("units")), regenerated,
			texts(result.get("pending")), result.path("stopped").asBoolean(false),
			result.path("publishable").asBoolean(false), blocks, map(result.get("meta")), manual);
	}

	// ---------------------------------------------------------------- A-13-17 반영

	/**
	 * 끝난 재생성 결과를 블록에 반영한다. 다시 만든 블록만 바꾸고 그 블록의 승인을 지운다(다시 검수 대기). 사건 행을 잠그고 한다 —
	 * 대본 승인 · 대사 수정(A-13-11 ~ 13)과 같은 잠금이라 서로 엇갈리지 않는다. 반영한 뒤 파이프라인 작업(사건 입력)을 지운다.
	 */
	public CaseScriptResponse apply(Long caseId, Long regenerationId, AdminPrincipal admin,
			HttpServletRequest httpRequest) {
		CaseScriptRegeneration entity = read(caseId, regenerationId);
		if (entity.isActive()) {
			entity = refresh(entity);	// 화면이 상태를 묻기 전에 눌렀을 수 있다 — 끝났는지 한 번 확인한다
		}
		if (entity.getStatus() != ScriptRegenerationStatus.SUCCEEDED || entity.isApplied()) {
			throw new BusinessException(ErrorCode.INVALID_STATE, entity.isApplied() ? "이미 반영했습니다."
				: "아직 끝나지 않았거나 실패한 재생성은 반영할 수 없습니다.");
		}
		JsonNode result = fetchResult(entity.getJobId());
		transactionTemplate.executeWithoutResult(status -> {
			caseRepository.findByIdForUpdate(caseId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));
			CaseScriptRegeneration managed = regenerationRepository.findByIdForUpdate(regenerationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_REGENERATION_NOT_FOUND));
			CaseScript script = scriptRepository.findByLegalCaseId(caseId)
				.orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
			String reason = notApplicable(managed, script, result);
			if (reason != null) {
				throw new BusinessException(ErrorCode.INVALID_STATE, reason);
			}
			Map<ScriptUnit, RegeneratedBlock> blocks = regeneratedBlocks(result, managed.getInputs());
			JsonNode meta = result.path("meta");
			List<Map<String, Object>> scriptReview = maps(result.get("review")).stream()
				.filter(issue -> ScriptUnit.find(String.valueOf(issue.get("unit"))).isEmpty()).toList();
			script.applyRegeneration(blocks, map(meta.get("cast")), meta.path("issue").isString()
				? meta.get("issue").asString() : null, scriptReview, result.path("stopped").asBoolean(false),
				result.path("model").isString() ? result.get("model").asString() : null);
			managed.markApplied(admin.adminId(), Instant.now());
			Map<String, Object> detail = new LinkedHashMap<>();
			detail.put("regenerationId", regenerationId);
			detail.put("units", blocks.keySet().stream().map(ScriptUnit::name).toList());
			auditLogger.record(admin, AdminAuditAction.CASE_SCRIPT_REGENERATE_APPLY, AuditTargetType.CASE, caseId,
				detail, httpRequest);
		});
		pipelineClient.deleteJobQuietly(entity.getJobId());
		return scriptService.getScript(caseId);
	}

	/**
	 * 결과의 블록을 반영할 값으로. {@code basis}의 사건 내용 · 기사 판은 파이프라인이 비워 보낸다 — 요청 당시 판으로 채운다(반영 전에
	 * 지금 판과 같은지 이미 확인했다). 만든 블록 목록에 있는데 블록이 없거나 내용이 없으면 결과가 깨진 것이라 거절한다
	 */
	private static Map<ScriptUnit, RegeneratedBlock> regeneratedBlocks(JsonNode result, Map<String, Object> snapshot) {
		Map<ScriptUnit, RegeneratedBlock> blocks = new LinkedHashMap<>();
		for (String name : texts(result.get("regenerated"))) {
			ScriptUnit unit = ScriptUnit.find(name).orElseThrow(() -> brokenResult(name));
			JsonNode block = result.path("blocks").get(name);
			Object content = block == null ? null : plain(block.get("content"));
			if (content == null) {
				throw brokenResult(name);
			}
			Map<String, Object> basis = map(block.get("basis"));
			Map<String, Object> filled = basis == null ? new LinkedHashMap<>() : new LinkedHashMap<>(basis);
			if (unit.basis().contains(ScriptUnit.CASE_BASIS)) {
				filled.put(ScriptUnit.CASE_BASIS, snapshot.get("case"));
			}
			if (unit.basis().contains(ScriptUnit.PAPER_BASIS)) {
				filled.put(ScriptUnit.PAPER_BASIS, snapshot.get("paper"));
			}
			blocks.put(unit, new RegeneratedBlock(content, block.path("revision").asInt(1), filled,
				maps(block.get("review")), objects(block.get("warnings")), maps(block.get("repairs"))));
		}
		if (blocks.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "다시 만든 블록이 없습니다. 다시 요청해 주세요.");
		}
		return blocks;
	}

	private static BusinessException brokenResult(String unit) {
		return new BusinessException(ErrorCode.IMPORT_RESULT_INVALID, "재생성 결과의 " + unit + " 블록을 읽을 수 없습니다.");
	}

	private JsonNode fetchResult(String jobId) {
		try {
			return pipelineClient.getRegenerationResult(jobId);
		} catch (PipelineException.NotFound e) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "재생성 결과가 사라졌습니다. 다시 요청해 주세요.");
		} catch (PipelineException e) {
			log.warn("재생성 결과를 가져오지 못했습니다: {}", e.getMessage());
			throw new BusinessException(ErrorCode.IMPORT_UNAVAILABLE, "재생성 결과를 가져오지 못했습니다.");
		}
	}

	// ---------------------------------------------------------------- 공통

	private CaseScriptRegeneration read(Long caseId, Long regenerationId) {
		return transactionTemplate.execute(status -> regenerationRepository.findByIdAndLegalCaseId(regenerationId, caseId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_REGENERATION_NOT_FOUND)));
	}

	/** 파이프라인에 물어 받아 적는다. 작업이 사라졌으면 실패로 닫고, 지금 확인할 수 없으면 아는 값을 그대로 둔다 */
	private CaseScriptRegeneration refresh(CaseScriptRegeneration entity) {
		PipelineJob job;
		try {
			job = pipelineClient.getJob(entity.getJobId());
		} catch (PipelineException.NotFound e) {
			return record(entity.getId(), ScriptRegenerationStatus.FAILED, ErrorCode.SCRIPT_REGENERATION_NOT_FOUND.name(),
				"재생성 작업이 사라졌습니다. 다시 요청해 주세요.", Instant.now(), null);
		} catch (PipelineException e) {
			log.warn("재생성 상태를 가져오지 못했습니다 (regenerationId={}): {}", entity.getId(), e.getMessage());
			return entity;
		}
		if (job == null) {
			return entity;
		}
		return record(entity.getId(), statusOf(job), errorCodeOf(job), job.error(), parseInstant(job.finishedAt()),
			stageSnapshot(job));
	}

	private CaseScriptRegeneration record(Long id, ScriptRegenerationStatus status, String errorCode, String message,
			Instant finishedAt, List<Map<String, String>> stages) {
		return transactionTemplate.execute(ignored -> {
			CaseScriptRegeneration managed = regenerationRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_REGENERATION_NOT_FOUND));
			managed.applyProgress(status, errorCode, message, finishedAt, stages);
			return managed;
		});
	}

	private Map<String, Object> currentSnapshot(CaseScript script) {
		LegalCase legalCase = script.getLegalCase();
		Integer paperRevision = paperRepository.findByLegalCaseId(legalCase.getId()).map(CasePaper::getRevision)
			.orElse(null);
		return ScriptRegenerationInputs.snapshot(legalCase, script, paperRevision);
	}

	private static ScriptRegenerationStatus statusOf(PipelineJob job) {
		try {
			return ScriptRegenerationStatus.valueOf(job.status());
		} catch (IllegalArgumentException | NullPointerException e) {
			log.warn("파이프라인이 모르는 상태를 보냈습니다: {}", job.status());
			return ScriptRegenerationStatus.RUNNING;
		}
	}

	private static String errorCodeOf(PipelineJob job) {
		return ScriptRegenerationStatus.FAILED.name().equals(job.status()) ? ErrorCode.INTERNAL_ERROR.name() : null;
	}

	/** 재생성은 대본 단계만 돈다 — 꺼진(SKIPPED) 등록 단계는 저장하지 않는다 */
	private static List<Map<String, String>> stageSnapshot(PipelineJob job) {
		if (job.stages() == null || job.stages().isEmpty()) {
			return null;
		}
		return job.stages().stream()
			.filter(stage -> stage != null && stage.name() != null && stage.status() != null
				&& !STAGE_SKIPPED.equals(stage.status()))
			.map(stage -> Map.of("name", stage.name(), "status", stage.status()))
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

	private static Object plain(JsonNode node) {
		return node == null || node.isNull() || node.isMissingNode() ? null : JSON.convertValue(node, Object.class);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(JsonNode node) {
		return node != null && node.isObject() ? (Map<String, Object>) plain(node) : null;
	}

	private static List<Map<String, Object>> maps(JsonNode node) {
		List<Map<String, Object>> items = new ArrayList<>();
		if (node != null && node.isArray()) {
			for (JsonNode item : node) {
				Map<String, Object> value = map(item);
				if (value != null) {
					items.add(value);
				}
			}
		}
		return items;
	}

	private static List<Object> objects(JsonNode node) {
		List<Object> items = new ArrayList<>();
		if (node != null && node.isArray()) {
			for (JsonNode item : node) {
				items.add(plain(item));
			}
		}
		return items.stream().filter(Objects::nonNull).toList();
	}

	private static List<String> texts(JsonNode node) {
		List<String> items = new ArrayList<>();
		if (node != null && node.isArray()) {
			for (JsonNode item : node) {
				if (item.isString()) {
					items.add(item.asString());
				}
			}
		}
		return new ArrayList<>(new LinkedHashSet<>(items));
	}
}
