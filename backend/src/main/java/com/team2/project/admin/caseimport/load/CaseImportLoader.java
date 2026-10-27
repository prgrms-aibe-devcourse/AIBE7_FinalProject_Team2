package com.team2.project.admin.caseimport.load;

import tools.jackson.databind.JsonNode;
import com.team2.project.admin.caseimport.domain.CaseImport;
import com.team2.project.admin.caseimport.domain.CaseImportSource;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.exception.UniqueViolations;
import com.team2.project.judgment.domain.AiGeneration;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.repository.AiGenerationRepository;
import com.team2.project.judgment.repository.JudgmentFactorRepository;
import com.team2.project.judgment.repository.JudgmentRepository;
import com.team2.project.legalcase.domain.CaseSource;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.CaseScript;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.SentencingGuideline;
import com.team2.project.legalcase.repository.CaseSectionRepository;
import com.team2.project.legalcase.repository.CaseScriptRepository;
import com.team2.project.legalcase.repository.CaseSourceRepository;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import com.team2.project.legalcase.repository.PenaltyRuleRepository;
import com.team2.project.legalcase.repository.SentencingGuidelineRepository;
import com.team2.project.pipeline.PipelineResult;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파이프라인 결과를 사건으로 적재한다 (BE-93 2차).
 *
 * <p>파이썬 쪽은 SQL 파일을 만들어 사람이 psql로 실행했다. 서비스 경로에서는 파이프라인의 적재 단계를
 * 아예 막아 두었으므로(COMMON-50) 여기가 사건을 만드는 유일한 길이다.
 *
 * <p><b>한 트랜잭션으로 묶는다.</b> 일곱 표에 걸쳐 쓰는데 중간에 실패하면 반쯤 만들어진 사건이 검수 목록에
 * 올라온다 — 그 상태는 관리자가 고칠 수도, 지울 수도 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseImportLoader {

	private final LegalCaseRepository caseRepository;
	private final CaseSectionRepository sectionRepository;
	private final PenaltyRuleRepository penaltyRuleRepository;
	private final FactorRepository factorRepository;
	private final CaseSourceRepository caseSourceRepository;
	private final CaseScriptRepository caseScriptRepository;
	private final JudgmentRepository judgmentRepository;
	private final JudgmentFactorRepository judgmentFactorRepository;
	private final AiGenerationRepository aiGenerationRepository;
	private final SentencingGuidelineRepository guidelineRepository;
	private final CaseContentMapper caseContentMapper;
	private final JudgmentMapper judgmentMapper;

	/** 범죄 유형 → 양형기준 분류. 지금은 살인만 적재돼 있고 나머지는 양형기준 없이 만들어진다 */
	private static final Map<CrimeType, String> GUIDELINE_CATEGORIES = Map.of(CrimeType.MURDER, "살인범죄");

	/**
	 * 적재한다. 끝나면 사건이 DRAFT로 만들어지고 판결 후보 둘이 승인 대기 상태로 붙는다.
	 *
	 * @return 만들어진 사건
	 */
	@Transactional
	public LegalCase load(CaseImport caseImport, PipelineResult result) {
		JsonNode content = result.caseContent();
		JsonNode report = result.report();
		String title = Json.requiredText(content, "title", "case.title");
		// uk_legal_case_title에 걸려 터지기 전에 막는다. 관리자가 제목을 고쳐 다시 시도할 수 있게 409로 알린다
		if (caseRepository.existsByTitle(title)) {
			throw new BusinessException(ErrorCode.DUPLICATE_TITLE,
				"같은 제목의 사건이 이미 있습니다. 제목을 바꾼 뒤 다시 등록해 주세요.");
		}

		LocalDate incidentDate = incidentDate(caseImport);
		LegalCase legalCase = caseContentMapper.toCase(content, report, incidentDate,
			guidelineFor(content, caseImport, incidentDate));
		saveCase(legalCase);

		sectionRepository.saveAll(caseContentMapper.sections(legalCase, content));
		penaltyRuleRepository.saveAll(caseContentMapper.penaltyRules(legalCase, content, report));
		List<Factor> factors = factorRepository.saveAll(caseContentMapper.factors(legalCase, content, report, result.axis()));
		caseSourceRepository.saveAll(sources(legalCase, caseImport));

		saveCourtJudgment(legalCase, factors, result);
		saveAiJudgment(legalCase, factors, result);
		saveScript(legalCase, result);
		return legalCase;
	}

	/**
	 * 사건을 저장한다. 제목 유니크 제약(uk_legal_case_title)에 걸리면 DUPLICATE_TITLE로 바꾼다.
	 *
	 * <p>위에서 existsByTitle로 먼저 보지만, <b>다른 등록이 같은 제목으로 동시에 적재되면</b> 둘 다 그 검사를
	 * 통과한다. 그때 올라오는 제약 위반은 전역 처리기가 409 INVALID_STATE로 바꾸는데, 적재 계약에 없는
	 * 코드라 관리자가 무엇을 해야 할지 알 수 없다 — 순차로 부딪혔을 때와 같은 응답을 주도록 맞춘다
	 * (PR #242 리뷰). 여기서 flush해야 커밋 전에 걸린다.
	 */
	private void saveCase(LegalCase legalCase) {
		try {
			caseRepository.saveAndFlush(legalCase);
		} catch (DataIntegrityViolationException e) {
			if (!UniqueViolations.isUniqueViolation(e)) {
				throw e;
			}
			// legal_case의 유니크 제약은 제목뿐이다 (V7 uk_legal_case_title)
			throw new BusinessException(ErrorCode.DUPLICATE_TITLE,
				"같은 제목의 사건이 이미 있습니다. 제목을 바꾼 뒤 다시 등록해 주세요.");
		}
	}

	/** 재판부 판결. 초안이 없으면 건너뛴다 — 사건은 만들어지고 관리자가 나중에 채울 수 있다 */
	private void saveCourtJudgment(LegalCase legalCase, List<Factor> factors, PipelineResult result) {
		JsonNode draft = result.courtDraft();
		if (draft == null || draft.isNull()) {
			log.warn("재판부 판결 초안이 없어 건너뜁니다 (jobId={})", result.jobId());
			return;
		}
		Judgment judgment = judgmentRepository.save(judgmentMapper.courtJudgment(legalCase, draft));
		judgmentFactorRepository.saveAll(judgmentMapper.judgmentFactors(judgment, factors, draft,
			"judgmentFactors", "evidence", "courtDraft"));
	}

	/** AI 판결 + 생성 기록. 생성 기록이 있어야 관리자가 어느 모델이 왜 그렇게 골랐는지 볼 수 있다 */
	private void saveAiJudgment(LegalCase legalCase, List<Factor> factors, PipelineResult result) {
		JsonNode output = result.judgment();
		Judgment judgment = judgmentRepository.save(judgmentMapper.aiJudgment(legalCase, output));
		judgmentFactorRepository.saveAll(judgmentMapper.judgmentFactors(judgment, factors, output,
			"factors", "reason", "judgment"));
		aiGenerationRepository.save(AiGeneration.pending(judgment, modelName(result), promptVersion(result),
			Map.of(), Json.toMap(output), generationReport(result)));
	}

	/**
	 * 사건 대본 (BE-108). 대본을 돌리지 않았거나 대사가 하나도 없으면 건너뛴다 — 재판부 초안이 없을 때와
	 * 같이 <b>사건은 그대로 만들어진다</b>. 대본은 나중에 들어와도 되고, 없으면 화면이 MVP 흐름으로 간다
	 * (frontend {@code scripts.js} "대본이 없는 사건은 MVP 화면 그대로").
	 *
	 * <p>파이프라인이 어긋난다고 표시한 대본({@code publishable: false})도 <b>적재는 한다</b> — 관리자가
	 * 검수 화면에서 무엇이 모자란지 보고 다시 생성할지 정해야 하기 때문이다. 대신 승인은 막는다
	 * ({@link CaseScript#approve}).
	 */
	private void saveScript(LegalCase legalCase, PipelineResult result) {
		JsonNode script = result.script();
		if (script == null || script.isNull()) {
			return;		// 대본을 돌리지 않은 등록이다 (기본값). 흔한 경우라 로그로 남기지 않는다
		}
		List<Map<String, Object>> evidence = Json.array(script, "evidence").stream()
			.map(Json::toMap).filter(Objects::nonNull).toList();
		if (evidence.isEmpty()) {
			log.warn("대본에 대사가 없어 건너뜁니다 (jobId={})", result.jobId());
			return;
		}
		caseScriptRepository.save(CaseScript.of(legalCase, evidence, Json.text(script, "model"),
			Json.toMap(script.get("cast")), Json.text(script, "balanceWarning"), Json.bool(script, "publishable"),
			generatedAt(script), Json.bool(script, "stopped")));
	}

	/**
	 * 파이프라인이 대본을 만든 시각. 형식이 다르거나 없으면 비워 둔다 — 화면에 보여 주는 값이라 적재를 막지 않는다
	 * (BE-93의 "모르는 필드는 지나가게 둔다"와 같은 원칙)
	 */
	private static Instant generatedAt(JsonNode script) {
		String value = Json.text(script, "createdAt");
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(value).toInstant();
		} catch (DateTimeParseException e) {
			log.warn("대본 생성 시각을 읽지 못해 비워 둡니다: {}", value);
			return null;
		}
	}

	/**
	 * 관리자 후검수용 생성 기록.
	 *
	 * <p>파이프라인의 `build_generation_report`는 <b>적재 단계 안에 있어서</b> 서비스 경로에서는 만들어지지
	 * 않는다(그 단계를 껐다). 그래서 결과로 받은 회차 선택 정보로 같은 값을 여기서 만든다.
	 *
	 * <p>파이썬은 중복 적재를 막으려고 `runKey`(출력 JSON 해시)를 넣는데 여기서는 쓰지 않는다 —
	 * 같은 등록 작업을 두 번 적재하지 못하게 막는 쪽은 `case_import.case_id`다.
	 */
	private static Map<String, Object> generationReport(PipelineResult result) {
		Map<String, Object> report = new LinkedHashMap<>();
		report.put("jobId", result.jobId());
		PipelineResult.Selection selection = result.selection();
		if (selection != null) {
			report.put("modelSpec", selection.modelSpec());
			report.put("runIndex", selection.runIndex());
			report.put("selection", Map.of("strategy", String.valueOf(selection.strategy()),
				"reason", String.valueOf(selection.reason())));
			report.put("validationWarnings", selection.warnings() == null ? List.of() : selection.warnings());
		}
		if (result.axis() != null && !result.axis().isNull()) {
			report.put("axis", Json.toMap(result.axis()));
		}
		return report;
	}

	/** 모델 이름 (`공급자:모델ID`에서 모델ID만). ai_generation.model_name은 50자까지다 */
	private static String modelName(PipelineResult result) {
		PipelineResult.Selection selection = result.selection();
		String spec = selection == null ? null : selection.modelSpec();
		if (spec == null || spec.isBlank()) {
			return "unknown";
		}
		String name = spec.contains(":") ? spec.substring(spec.indexOf(':') + 1) : spec;
		return name.length() <= 50 ? name : name.substring(0, 50);
	}

	private static String promptVersion(PipelineResult result) {
		String version = Json.text(result.report(), "promptVersion");
		return version == null ? "unknown" : version;
	}

	/**
	 * 등록 요청에 들어온 판결문을 사건에 붙인다. <b>원문을 여기서 옮기고 원래 자리는 비운다</b> —
	 * 같은 원문이 두 벌로 남지 않게 한다 (case_import_source 머리말, FR-5-3).
	 */
	private List<CaseSource> sources(LegalCase legalCase, CaseImport caseImport) {
		List<CaseImportSource> imported = caseImport.getSources();
		int finalIndex = finalSourceIndex(imported);
		List<CaseSource> sources = new java.util.ArrayList<>();
		for (int i = 0; i < imported.size(); i++) {
			CaseImportSource source = imported.get(i);
			sources.add(CaseSource.of(legalCase, source.getCourtLevel(), source.getCaseNumber(),
				source.getCourtName(), source.getDecidedAt(), i == finalIndex,
				source.getSourceOrg() == null ? DEFAULT_SOURCE_ORG : source.getSourceOrg(),
				source.getOriginalText(), source.getNote()));
			source.clearOriginalText();
		}
		return sources;
	}

	/** 출처 기관 기본값 (파이썬 case_seed_sql.DEFAULT_SOURCE_ORG와 같다) */
	private static final String DEFAULT_SOURCE_ORG = "법원 공개 판결문";

	/**
	 * 최종 확정 판결의 번호. 관리자가 표시했으면 그것을 쓰고, 아무도 표시하지 않았으면
	 * <b>심급이 가장 높은 것, 같으면 뒤의 것</b>을 고른다 (파이프라인 resolve_sources와 같은 규칙).
	 */
	private static int finalSourceIndex(List<CaseImportSource> sources) {
		for (int i = 0; i < sources.size(); i++) {
			if (sources.get(i).isFinal()) {
				return i;
			}
		}
		int best = 0;
		for (int i = 0; i < sources.size(); i++) {
			if (sources.get(i).getCourtLevel().ordinal() >= sources.get(best).getCourtLevel().ordinal()) {
				best = i;
			}
		}
		return best;
	}

	/** 사건 발생일. 등록 요청에는 없으므로 가장 이른 선고일을 쓴다 */
	private static LocalDate incidentDate(CaseImport caseImport) {
		return caseImport.getSources().stream()
			.map(CaseImportSource::getDecidedAt)
			.filter(java.util.Objects::nonNull)
			.min(LocalDate::compareTo)
			.orElse(null);
	}

	/** 양형기준 버전. 기준일에 맞는 것이 없으면 비워 둔다 (검수에서 채울 수 있다) */
	private SentencingGuideline guidelineFor(JsonNode content, CaseImport caseImport, LocalDate incidentDate) {
		String category = GUIDELINE_CATEGORIES.get(caseImport.getCrimeType());
		if (category == null || incidentDate == null) {
			return null;
		}
		Long id = guidelineRepository.findIdApplicableAt(category, incidentDate);
		return id == null ? null : guidelineRepository.findById(id).orElse(null);
	}
}
