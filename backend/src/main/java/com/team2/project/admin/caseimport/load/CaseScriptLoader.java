package com.team2.project.admin.caseimport.load;

import com.team2.project.imagegen.service.CasePaperService;
import com.team2.project.imagegen.service.CasePaperService.GeneratedPaper;
import com.team2.project.legalcase.domain.CasePaper;
import com.team2.project.legalcase.domain.CaseScript;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.ScriptAnswers;
import com.team2.project.legalcase.domain.ScriptUnit;
import com.team2.project.legalcase.repository.CaseScriptRepository;
import com.team2.project.pipeline.PipelineResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 사건 대본 적재 (BE-108, 블록 저장은 BE-126). {@link CaseImportLoader}가 같은 트랜잭션 안에서 부른다.
 *
 * <p>파이프라인 결과({@code result.script})는 두 모양이다.
 * <ul>
 *   <li><b>대본 전체</b>(BE-121, {@code stages.script.full = true}): {@code fullScript.blocks}(U0 ~ U4) · {@code fullScript.meta}를
 *       블록 · 메타로 저장한다. 형식은 `tools/ai-judgment/prompts/script/README.md` "저장 블록"</li>
 *   <li><b>증거조사 대사만</b>(BE-107): {@code evidence}를 U3 블록 하나로 저장한다</li>
 * </ul>
 *
 * <p>블록의 {@code basis}(만들 때 본 값)에서 사건 내용 · 기사는 파이프라인이 {@code null}로 보낸다 — DB가 가진 값이라
 * 여기서 DB의 판으로 채운다({@link ScriptUnit#DB_BASIS}). 생성 직후 적재하므로 지금의 판이 곧 생성 입력이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CaseScriptLoader {

	private final CaseScriptRepository scriptRepository;
	private final CasePaperService paperService;

	/**
	 * 대본을 돌리지 않았거나 블록 · 대사가 하나도 없으면 건너뛴다 — 재판부 초안이 없을 때와 같이 <b>사건은 그대로
	 * 만들어진다</b>. 대본은 나중에 들어와도 되고, 없으면 화면이 MVP 흐름으로 간다(frontend {@code scripts.js}).
	 *
	 * <p>파이프라인이 어긋난다고 표시한 대본({@code publishable: false})도 <b>적재는 한다</b> — 관리자가 검수 화면에서
	 * 무엇이 모자란지 보고 다시 생성할지 정해야 하기 때문이다. 대신 승인은 막는다({@link CaseScript#approve}).
	 */
	void save(LegalCase legalCase, PipelineResult result) {
		JsonNode script = result.script();
		if (script == null || script.isNull()) {
			return;		// 대본을 돌리지 않은 등록이다 (기본값). 흔한 경우라 로그로 남기지 않는다
		}
		JsonNode full = script.get("fullScript");
		CaseScript saved = full != null && full.isObject() && full.has("blocks")
			? fullScript(legalCase, script, full, result)
			: evidenceOnly(legalCase, script, result);
		if (saved != null) {
			scriptRepository.save(saved);
		}
	}

	/** 대본 전체: 블록 · 메타 · 기사 */
	private CaseScript fullScript(LegalCase legalCase, JsonNode script, JsonNode full, PipelineResult result) {
		JsonNode blocks = full.get("blocks");
		if (blocks == null || !blocks.isObject() || blocks.isEmpty()) {
			log.warn("대본에 블록이 없어 건너뜁니다 (jobId={})", result.jobId());
			return null;
		}
		Integer paperRevision = savePaper(legalCase, full.get("paper"), result).map(CasePaper::getRevision).orElse(null);
		JsonNode meta = full.get("meta");
		List<String> pending = script.has("pending") ? Json.stringArray(script, "pending")
			: Json.stringArray(full, "pending");
		// 대본 전체에 걸린 검수 결과(REVIEW_FAILED, unit "")만 대본에 둔다. 블록 것은 블록이 갖는다
		List<Map<String, Object>> scriptReview = maps(full, "review").stream()
			.filter(issue -> ScriptUnit.find(String.valueOf(issue.get("unit"))).isEmpty()).toList();
		boolean publishable = script.has("scriptPublishable") ? Json.bool(script, "scriptPublishable")
			: Json.bool(full, "publishable");

		CaseScript caseScript = CaseScript.of(legalCase, model(script, full), Json.toMap(script.get("cast")),
			Json.text(meta, "issue"), admits(blocks.get(ScriptUnit.U0.name())), pending, scriptReview,
			Json.text(script, "balanceWarning"), publishable, generatedAt(script), Json.bool(script, "stopped"));
		for (ScriptUnit unit : ScriptUnit.values()) {
			JsonNode block = blocks.get(unit.name());
			if (block == null || !block.isObject()) {
				continue;		// 만들지 못한 단위 — pending이 알린다
			}
			Object content = Json.toPlain(block.get("content"));
			if (content == null) {
				log.warn("대본 블록 {}에 내용이 없어 건너뜁니다 (jobId={})", unit, result.jobId());
				continue;
			}
			Integer revision = Json.integer(block, "revision");
			Map<String, Object> basis = Optional.ofNullable(Json.toMap(block.get("basis"))).orElseGet(LinkedHashMap::new);
			fillDbBasis(unit, basis, legalCase, paperRevision);
			caseScript.addBlock(unit, content, revision == null ? 1 : revision, basis, maps(block, "review"),
				objects(block, "warnings"), maps(block, "repairs"));
		}
		if (caseScript.getBlocks().isEmpty()) {
			log.warn("대본에 읽을 수 있는 블록이 없어 건너뜁니다 (jobId={})", result.jobId());
			return null;
		}
		return caseScript;
	}

	/** 증거조사 대사만 (BE-107): U3 블록 하나 */
	private CaseScript evidenceOnly(LegalCase legalCase, JsonNode script, PipelineResult result) {
		List<Object> evidence = Json.array(script, "evidence").stream()
			.map(Json::toMap).filter(Objects::nonNull).map(line -> (Object) line).toList();
		if (evidence.isEmpty()) {
			log.warn("대본에 대사가 없어 건너뜁니다 (jobId={})", result.jobId());
			return null;
		}
		Map<String, Object> cast = Json.toMap(script.get("cast"));
		CaseScript caseScript = CaseScript.of(legalCase, Json.text(script, "model"), cast, null, null, List.of(),
			List.of(), Json.text(script, "balanceWarning"), Json.bool(script, "publishable"), generatedAt(script),
			Json.bool(script, "stopped"));
		// 이 경로는 생성 근거를 내보내지 않는다. 아는 값(사건 내용 판 · 배역)만 남기고, 분류 값은 비워 둔다
		Map<String, Object> basis = new LinkedHashMap<>();
		basis.put(ScriptUnit.CASE_BASIS, legalCase.getContentRevision());
		basis.put("cast", cast);
		caseScript.addBlock(ScriptUnit.U3, evidence, 1, basis, List.of(), List.of(), List.of());
		return caseScript;
	}

	/**
	 * 파이프라인이 만든 기사를 사건의 신문 지면(API 17)으로 저장한다. U1(1막 해설)은 이 기사를 보고 만들어졌다 — 저장하지 않으면
	 * 관리자가 따로 만든 다른 기사(A-13-6) 옆에 이 기사를 해설하는 대사가 붙는다. 지면 규칙(A-13-8)에 맞지 않으면 저장하지 않고
	 * U1의 {@code basis.paper}를 비워 둔다(어느 기사를 보고 만들었는지 모르는 블록).
	 */
	private Optional<CasePaper> savePaper(LegalCase legalCase, JsonNode paper, PipelineResult result) {
		if (paper == null || !paper.isObject()) {
			return Optional.empty();
		}
		Optional<GeneratedPaper> checked = CasePaperService.checked(new GeneratedPaper(Json.text(paper, "headline"),
			Json.text(paper, "deck"), Json.text(paper, "caption"),
			Json.array(paper, "article").stream().map(item -> item.isNull() ? null : item.asText()).toList()));
		if (checked.isEmpty()) {
			log.warn("대본의 기사가 지면 규칙에 맞지 않아 저장하지 않습니다 (jobId={})", result.jobId());
			return Optional.empty();
		}
		return Optional.of(paperService.savePaper(legalCase, checked.get()));
	}

	/** DB가 가진 값(사건 내용 · 기사)을 지금의 판으로 채운다. 그 블록이 보지 않는 값은 넣지 않는다 */
	private static void fillDbBasis(ScriptUnit unit, Map<String, Object> basis, LegalCase legalCase,
			Integer paperRevision) {
		if (unit.basis().contains(ScriptUnit.CASE_BASIS)) {
			basis.put(ScriptUnit.CASE_BASIS, legalCase.getContentRevision());
		}
		if (unit.basis().contains(ScriptUnit.PAPER_BASIS)) {
			basis.put(ScriptUnit.PAPER_BASIS, paperRevision);
		}
	}

	/**
	 * 혐의 인정 여부 (메타 admits). 파이프라인 값을 받지 않고 U0 세 번째 답에서 서버가 다시 계산한다 — 관리자가 U0를 고치면
	 * 서버가 다시 계산해야 하므로(2차) 처음부터 같은 규칙({@link ScriptAnswers})으로 정한다. U0가 없으면 null
	 */
	private static Boolean admits(JsonNode design) {
		if (design == null || !design.isObject()) {
			return null;
		}
		List<JsonNode> questions = Json.array(design.get("content"), "questions");
		return questions.size() > 2 ? ScriptAnswers.admits(Json.text(questions.get(2), "a")) : null;
	}

	private static String model(JsonNode script, JsonNode full) {
		String model = Json.text(script, "model");
		return model != null ? model : Json.text(full, "model");
	}

	private static List<Map<String, Object>> maps(JsonNode node, String field) {
		return Json.array(node, field).stream().map(Json::toMap).filter(Objects::nonNull).toList();
	}

	private static List<Object> objects(JsonNode node, String field) {
		return Json.array(node, field).stream().map(Json::toPlain).filter(Objects::nonNull).toList();
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
}
