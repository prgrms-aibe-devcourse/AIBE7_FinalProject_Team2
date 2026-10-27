package com.team2.project.admin.caseimport.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.team2.project.admin.caseimport.domain.CaseImport;
import com.team2.project.admin.caseimport.domain.CaseImportSource;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.legalcase.domain.CaseStatus;
import com.team2.project.legalcase.domain.CourtLevel;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.pipeline.PipelineResult;
import com.team2.project.support.ApiIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 파이프라인 결과 → 사건 적재 (BE-93 2차).
 *
 * <p>실제 DB에 쓴다. 적재는 일곱 표에 걸쳐 쓰고 DB 제약(CHECK · 유니크 · FK)이 그중 상당수를 지키므로,
 * 가짜 저장소로는 맞는지 확인할 수 없다.
 */
class CaseImportLoaderTest extends ApiIntegrationTest {

	@Autowired CaseImportLoader loader;

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final String TITLE_PREFIX = "적재 테스트 사건 ";

	private String title;

	/**
	 * 이 테스트가 만든 사건을 모두 지운다.
	 *
	 * <p>제목 하나만 보고 지우면, 한 테스트에서 사건 입력을 여러 번 만든 경우 먼저 만든 사건이 남는다.
	 * 남은 case_source의 사건번호는 <b>다른 테스트의 중복 검사에 걸려</b> 엉뚱한 곳을 깨뜨린다(실제로 겪었다).
	 */
	@AfterEach
	void cleanUp() {
		List<Long> caseIds = jdbcTemplate.query("SELECT id FROM legal_case WHERE title LIKE ?",
			(rs, n) -> rs.getLong(1), TITLE_PREFIX + "%");
		for (Long caseId : caseIds) {
			jdbcTemplate.update("""
				DELETE FROM ai_generation WHERE judgment_id IN (SELECT id FROM judgment WHERE case_id = ?)""", caseId);
			jdbcTemplate.update("""
				DELETE FROM judgment_factor WHERE judgment_id IN (SELECT id FROM judgment WHERE case_id = ?)""", caseId);
			for (String table : List.of("judgment", "case_source", "factor", "penalty_rule", "case_section")) {
				jdbcTemplate.update("DELETE FROM " + table + " WHERE case_id = ?", caseId);
			}
			jdbcTemplate.update("DELETE FROM legal_case WHERE id = ?", caseId);
		}
	}

	@Test
	@DisplayName("적재하면 사건이 DRAFT로 만들어지고 섹션 · 형벌 규칙 · 판단 요소 · 원본이 함께 들어간다")
	void load_createsCaseContent() {
		CaseImport caseImport = caseImport();

		LegalCase legalCase = loader.load(caseImport, result());

		assertThat(legalCase.getStatus()).isEqualTo(CaseStatus.DRAFT);
		assertThat(legalCase.getTitle()).isEqualTo(title);
		Long caseId = legalCase.getId();
		assertThat(count("case_section", caseId)).isEqualTo(2);
		assertThat(count("penalty_rule", caseId)).isEqualTo(1);
		assertThat(count("factor", caseId)).isEqualTo(2);
		assertThat(count("case_source", caseId)).isEqualTo(1);
	}

	@Test
	@DisplayName("절도 사건도 적재된다 — 양형기준 데이터가 살인만 있어 양형기준 없이 만들어진다 (BE-112, V31)")
	void load_theftCase_isLoadedWithoutGuideline() {
		CaseImport caseImport = caseImport(CrimeType.THEFT);
		// 추출 결과(case.json)의 범죄 유형도 THEFT로 바꿔 넣는다 (CaseContentMapper가 enum으로 읽는다)
		JsonNode theftContent = node(caseJson().replace("\"crimeType\": \"MURDER\"", "\"crimeType\": \"THEFT\""));

		LegalCase legalCase = loader.load(caseImport, resultWith(theftContent));

		assertThat(legalCase.getCrimeType()).isEqualTo(CrimeType.THEFT);
		assertThat(jdbcTemplate.queryForObject("SELECT guideline_id FROM legal_case WHERE id = ?", Long.class,
			legalCase.getId())).isNull();
	}

	@Test
	@DisplayName("판단 요소의 display_order는 배열 순서가 아니라 factorId다 (판결이 이 번호로 요소를 찾는다)")
	void load_factorDisplayOrderIsFactorId() {
		LegalCase legalCase = loader.load(caseImport(), result());

		assertThat(jdbcTemplate.queryForList(
			"SELECT display_order FROM factor WHERE case_id = ? ORDER BY display_order", Integer.class,
			legalCase.getId())).containsExactly(7, 9);	// 사건 입력의 factorId 그대로
	}

	@Test
	@DisplayName("AI · 재판부 판결은 공개하지 않은 상태로 들어가고, AI는 후검수 대기가 된다")
	void load_judgmentsAreUnpublished() {
		LegalCase legalCase = loader.load(caseImport(), result());
		Long caseId = legalCase.getId();

		assertThat(jdbcTemplate.queryForList(
			"SELECT subject_type FROM judgment WHERE case_id = ? ORDER BY subject_type", String.class, caseId))
			.containsExactly("AI", "COURT");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM judgment WHERE case_id = ? AND is_published", Integer.class, caseId)).isZero();
		assertThat(jdbcTemplate.queryForObject("""
			SELECT g.review_status FROM ai_generation g JOIN judgment j ON j.id = g.judgment_id
			WHERE j.case_id = ?""", String.class, caseId)).isEqualTo("PENDING");
	}

	@Test
	@DisplayName("작용 요소는 factorId로 판단 요소를 찾아 붙는다 (재판부는 근거 문장까지)")
	void load_judgmentFactorsResolveByFactorId() {
		LegalCase legalCase = loader.load(caseImport(), result());

		assertThat(jdbcTemplate.queryForObject("""
			SELECT f.display_order FROM judgment_factor jf
			JOIN judgment j ON j.id = jf.judgment_id JOIN factor f ON f.id = jf.factor_id
			WHERE j.case_id = ? AND j.subject_type = 'COURT'""", Integer.class, legalCase.getId())).isEqualTo(9);
		assertThat(jdbcTemplate.queryForObject("""
			SELECT jf.evidence FROM judgment_factor jf JOIN judgment j ON j.id = jf.judgment_id
			WHERE j.case_id = ? AND j.subject_type = 'COURT'""", String.class, legalCase.getId()))
			.isEqualTo("피해 회복이 이루어졌다");
	}

	@Test
	@DisplayName("판결문 원문을 case_source로 옮기고 등록 쪽은 비운다 (같은 원문을 두 벌로 두지 않는다)")
	void load_movesOriginalText() {
		CaseImport caseImport = caseImport();

		LegalCase legalCase = loader.load(caseImport, result());

		assertThat(jdbcTemplate.queryForObject(
			"SELECT original_text FROM case_source WHERE case_id = ?", String.class, legalCase.getId()))
			.isEqualTo("판결문 전문입니다");
		assertThat(caseImport.getSources().get(0).getOriginalText()).isNull();
	}

	@Test
	@DisplayName("같은 제목의 사건이 이미 있으면 409 DUPLICATE_TITLE — 반쯤 만들어진 사건을 남기지 않는다")
	void load_duplicateTitle() {
		PipelineResult same = result();	// 두 번 다 같은 제목을 쓴다
		loader.load(caseImport(), same);

		assertThatThrownBy(() -> loader.load(caseImport(), same))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_TITLE);
	}

	@Test
	@DisplayName("사전 판단에 쓸 요소(OVERVIEW)가 없으면 적재하지 않는다 — 공개할 수 없는 사건이 된다")
	void load_withoutOverviewFactor() {
		JsonNode result = node(caseJson().replace("\"OVERVIEW\"", "\"DETAIL\""));

		assertThatThrownBy(() -> loader.load(caseImport(), resultWith(result)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.IMPORT_RESULT_INVALID);
	}

	@Test
	@DisplayName("판결이 사건에 없는 판단 요소를 가리키면 막는다 (작용 요소가 조용히 사라지지 않게)")
	void load_judgmentPointsToUnknownFactor() {
		PipelineResult broken = new PipelineResult("job", node(caseJson()), node(reportJson()),
			node(courtDraftJson()), node(aiJudgmentJson().replace("\"factorId\": 7", "\"factorId\": 99")),
			new PipelineResult.Selection("gemini:test", 1, "consensus", "합의", List.of()), null);

		assertThatThrownBy(() -> loader.load(caseImport(), broken))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.IMPORT_RESULT_INVALID);
	}

	@Test
	@DisplayName("가치관 축은 투표 결과를 쓰고 투표 기록을 남긴다 (추출기 값보다 다수결이 낫다)")
	void load_usesAxisVotes() {
		PipelineResult withVotes = new PipelineResult("job", node(caseJson()), node(reportJson()),
			node(courtDraftJson()), node(aiJudgmentJson()),
			new PipelineResult.Selection("gemini:test", 1, "consensus", "합의", List.of()),
			node("""
				{"runs": 5, "factors": [
				  {"factorId": 7, "valueAxis": "PRINCIPLE_RELATION",
				   "valueAxisVotes": {"runs": 5, "counts": {"PRINCIPLE_RELATION": 4, "FAULT_STANDARD": 1},
				                      "needsReview": false, "requestedRuns": 5}}]}"""));

		LegalCase legalCase = loader.load(caseImport(), withVotes);

		// 추출기는 FAULT_STANDARD였지만 투표가 PRINCIPLE_RELATION으로 뒤집었다
		assertThat(jdbcTemplate.queryForObject(
			"SELECT value_axis FROM factor WHERE case_id = ? AND display_order = 7", String.class,
			legalCase.getId())).isEqualTo("PRINCIPLE_RELATION");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT value_axis_votes::text FROM factor WHERE case_id = ? AND display_order = 7", String.class,
			legalCase.getId())).contains("PRINCIPLE_RELATION", "needsReview");
		// 투표가 없던 요소는 추출기 값을 그대로 쓰고 기록은 비어 있다
		assertThat(jdbcTemplate.queryForObject(
			"SELECT value_axis FROM factor WHERE case_id = ? AND display_order = 9", String.class,
			legalCase.getId())).isEqualTo("APOLOGY_SINCERITY");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT value_axis_votes FROM factor WHERE case_id = ? AND display_order = 9", String.class,
			legalCase.getId())).isNull();
		// 자동 분류 결과이므로 관리자 확정 전 상태다
		assertThat(jdbcTemplate.queryForList(
			"SELECT value_axis_status FROM factor WHERE case_id = ?", String.class, legalCase.getId()))
			.containsOnly("AUTO");
	}

	@Test
	@DisplayName("AI 판결의 참고 자료 태그를 적재한다 (화면 S-06이 보여 주는 값)")
	void load_keepsReferenceTags() {
		LegalCase legalCase = loader.load(caseImport(), result());

		assertThat(jdbcTemplate.queryForObject("""
			SELECT reference_tags::text FROM judgment WHERE case_id = ? AND subject_type = 'AI'""",
			String.class, legalCase.getId())).contains("양형기준");
		// 재판부 판결에는 없는 값이다
		assertThat(jdbcTemplate.queryForObject("""
			SELECT reference_tags::text FROM judgment WHERE case_id = ? AND subject_type = 'COURT'""",
			String.class, legalCase.getId())).isEqualTo("[]");
	}

	@Test
	@DisplayName("같은 factorId가 두 번 오면 422로 막는다 (display_order가 겹쳐 판결 연결이 깨진다)")
	void load_duplicateFactorId() {
		JsonNode broken = node(caseJson().replace("\"factorId\": 9", "\"factorId\": 7"));

		assertThatThrownBy(() -> loader.load(caseImport(), resultWith(broken)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.IMPORT_RESULT_INVALID);
	}

	// ---------------------------------------------------------------- 도우미

	private int count(String table, Long caseId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE case_id = ?",
			Integer.class, caseId);
	}

	private CaseImport caseImport() {
		return caseImport(CrimeType.MURDER);
	}

	private CaseImport caseImport(CrimeType crimeType) {
		CaseImport entity = CaseImport.start(UUID.randomUUID().toString().replace("-", ""), crimeType);
		entity.addSource(CaseImportSource.of(CourtLevel.FIRST, "2099고합001", "가상지방법원",
			LocalDate.of(2025, 1, 10), true, null, "판결문 전문입니다", null));
		return entity;
	}

	private PipelineResult result() {
		return resultWith(node(caseJson()));
	}

	private PipelineResult resultWith(JsonNode caseContent) {
		return new PipelineResult("job", caseContent, node(reportJson()), node(courtDraftJson()),
			node(aiJudgmentJson()),
			new PipelineResult.Selection("gemini:test", 1, "consensus", "합의", List.of()), null);
	}

	private static JsonNode node(String json) {
		try {
			return MAPPER.readTree(json);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private String caseJson() {
		title = TITLE_PREFIX + UUID.randomUUID();
		return """
			{"title": "%s", "crimeType": "MURDER", "chargeName": "살인", "overview": "사건 개요",
			 "appliedLaw": "형법 제250조", "statutoryPenaltyText": "사형 · 무기 또는 5년 이상의 징역",
			 "listing": {"shortIntro": "짧은 소개", "keywords": ["금전", "지인"], "difficulty": "MID",
			             "estimatedMinutes": 15},
			 "recommended": {"minMonths": 120, "maxMonths": 192, "basis": "권고 근거"},
			 "sections": [{"stage": "DETAIL", "sectionType": "FACTS", "title": "사실관계", "content": "본문"},
			              {"stage": "LAW", "sectionType": "LAW_TERM", "title": "법률 용어",
			               "data": [{"term": "고의", "desc": "알고도 한 것"}]}],
			 "penaltyRules": [{"penaltyType": "PRISON", "statutoryMin": 60, "statutoryMax": 600,
			                   "allowedMin": 60, "allowedMax": 600, "suspensionAllowed": true}],
			 "factors": [{"factorId": 7, "label": "계획적이었다", "revealStage": "OVERVIEW"},
			             {"factorId": 9, "label": "피해를 회복했다", "revealStage": "DETAIL"}]}
			""".formatted(title);
	}

	private static String reportJson() {
		return """
			{"promptVersion": "judgment-v3", "deidentifiedItems": ["이름", "주소"],
			 "penaltyRuleBasis": {"PRISON": "선고 가능 범위 근거"},
			 "factorExtras": [{"factorId": 7, "preLabel": "계획", "summaryTag": "계획성",
			                   "valueAxis": "FAULT_STANDARD"},
			                  {"factorId": 9, "preLabel": "회복", "summaryTag": "피해회복",
			                   "valueAxis": "APOLOGY_SINCERITY"}]}
			""";
	}

	private static String courtDraftJson() {
		return """
			{"judgment": {"penaltyType": "PRISON", "prisonMonths": 144, "summary": "징역 12년",
			              "reasoning": "재판부 판단 이유", "plainExplanation": "쉬운 설명",
			              "excerpt": "판결문 발췌", "extraDispositions": [{"type": "CONFISCATION", "value": "흉기"}]},
			 "judgmentFactors": [{"factorId": 9, "direction": "DOWN", "evidence": "피해 회복이 이루어졌다"}]}
			""";
	}

	private static String aiJudgmentJson() {
		return """
			{"penaltyType": "PRISON", "prisonMonths": 168, "summary": "징역 14년",
			 "reasoning": "AI 판단 이유", "referenceTags": ["양형기준"],
			 "factors": [{"factorId": 7, "direction": "UP", "reason": "계획적이었다"}]}
			""";
	}
}
