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
import com.team2.project.legalcase.service.CaseCastAssigner;
import com.team2.project.pipeline.PipelineResult;
import com.team2.project.support.ApiIntegrationTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
			for (String table : List.of("judgment", "case_source", "factor", "penalty_rule", "case_section",
				"case_script")) {
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
			new PipelineResult.Selection("gemini:test", 1, "consensus", "합의", List.of()), null, null, null);

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
				                      "needsReview": false, "requestedRuns": 5}}]}"""), null, null);

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

	@Test
	@DisplayName("대본(script)이 함께 오면 case_script에 비공개 · 검수 대기로 들어간다 (BE-108)")
	void load_savesScript() {
		CaseImport caseImport = caseImport();

		LegalCase legalCase = loader.load(caseImport, resultWithScript(node(scriptJson())));

		Long caseId = legalCase.getId();
		assertThat(count("case_script", caseId)).isEqualTo(1);
		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM case_script WHERE case_id = ?", caseId);
		assertThat(row.get("model")).isEqualTo("google:gemini-2.5-flash");
		assertThat(row.get("publishable")).isEqualTo(true);
		assertThat(row.get("stopped")).isEqualTo(false);
		assertThat(row.get("balance_warning")).isNull();
		assertThat(row.get("generated_at")).isNotNull();	// createdAt을 읽어 둔다 (적재 시각과 다르다)
		assertThat(row.get("review_status")).isNull();		// 비어 있으면 검수 대기 (judgment V16과 같은 방식)
		assertThat(String.valueOf(row.get("evidence"))).contains("계약서").contains("\"factorId\"");
		assertThat(String.valueOf(row.get("cast_roles"))).contains("나로");
	}

	@Test
	@DisplayName("대본이 없거나 대사가 비어 있으면 대본만 건너뛰고 사건은 그대로 만든다 (대본은 나중에 따로 들어올 수 있다)")
	void load_withoutScript_stillCreatesCase() {
		LegalCase noScript = loader.load(caseImport(), result());
		assertThat(count("case_script", noScript.getId())).isZero();

		LegalCase emptyEvidence = loader.load(caseImport(), resultWithScript(node("""
			{"evidence": [], "publishable": false, "model": "google:gemini-2.5-flash"}""")));
		assertThat(count("case_script", emptyEvidence.getId())).isZero();
		assertThat(emptyEvidence.getStatus()).isEqualTo(CaseStatus.DRAFT);
	}

	// ---------------------------------------------------------------- 배역 · 민감 플래그 (BE-125)

	@Test
	@DisplayName("분류 결과의 민감 플래그를 사건에 적는다 — 모르는 값도 이름 그대로, 겹친 값은 하나로 (BE-125)")
	void load_savesSensitiveFlags() {
		LegalCase legalCase = loader.load(caseImport(), resultOf(node(caseJson()), null, node("""
			{"model": "openai:test", "runs": 3, "needsReview": ["MENTAL_HEALTH"],
			 "classification": {"mood": "tragic", "issue": "mental_state", "victim": "forgive", "thirdParty": true,
			                    "sensitiveFlags": ["MENTAL_HEALTH", "CAPITAL_PENALTY", "NOT_A_FLAG", "MENTAL_HEALTH"]}}""")));

		assertThat(jdbcTemplate.queryForObject("SELECT sensitive_flags::text FROM legal_case WHERE id = ?",
			String.class, legalCase.getId())).isEqualTo("[\"MENTAL_HEALTH\", \"CAPITAL_PENALTY\", \"NOT_A_FLAG\"]");
	}

	@Test
	@DisplayName("분류를 돌리지 않은 등록은 민감 플래그를 비워 둔다(null) — '켜진 것 없음([])'과 구분한다")
	void load_withoutClassify_leavesFlagsUnset() {
		LegalCase notClassified = loader.load(caseImport(), result());
		LegalCase noFlags = loader.load(caseImport(), resultOf(node(caseJson()), null, node("""
			{"classification": {"mood": "calculated", "sensitiveFlags": []}}""")));

		assertThat(jdbcTemplate.queryForObject("SELECT sensitive_flags::text FROM legal_case WHERE id = ?",
			String.class, notClassified.getId())).isNull();
		assertThat(jdbcTemplate.queryForObject("SELECT sensitive_flags::text FROM legal_case WHERE id = ?",
			String.class, noFlags.getId())).isEqualTo("[]");
	}

	@Test
	@DisplayName("대본이 함께 오면 대본을 만든 배역을 사건 배역으로 쓴다 — 대사에 캐릭터 이름이 들어가 있다 (BE-125)")
	void load_scriptCastWins() {
		LegalCase legalCase = loader.load(caseImport(), resultOf(node(caseJson()), node("""
			{"evidence": [{"factorId": 7, "role": "prosecutor", "character": "nambul", "text": "계획적이었습니다."}],
			 "cast": {"prosecutor": "nambul", "defense": "naero"},
			 "model": "openai:test", "publishable": true}"""), null));

		assertThat(castOf(legalCase.getId())).isEqualTo(Map.of("prosecutor", "nambul", "defense", "naero"));
	}

	@Test
	@DisplayName("대본에 배역이 있는데 읽을 수 없으면 규칙으로 덮지 않고 배정하지 않는다 — 생성 기록에 남긴다 (BE-125)")
	void load_unreadableScriptCast_leavesCastUnassigned() {
		LegalCase legalCase = loader.load(caseImport(), resultOf(node(caseJson()), node("""
			{"evidence": [{"factorId": 7, "role": "prosecutor", "character": "nambul", "text": "계획적이었습니다."}],
			 "cast": {"prosecutor": "nambul", "defense": "naero", "codefendant": "naero"},
			 "model": "openai:test", "publishable": true}"""), null));

		assertThat(jdbcTemplate.queryForObject("SELECT cast_roles::text FROM legal_case WHERE id = ?",
			String.class, legalCase.getId())).isNull();
		assertThat(jdbcTemplate.queryForObject("""
			SELECT g.generation_report->>'castWarning' FROM ai_generation g
			JOIN judgment j ON j.id = g.judgment_id WHERE j.case_id = ?""", String.class, legalCase.getId()))
			.contains("배역");
	}

	@Test
	@DisplayName("대본이 없으면 같은 범죄 유형의 배정 수 · 마지막 배정을 보고 정한다 (BE-125)")
	void load_assignsCastByCrimeType() {
		String[] expected = expectedSequence("THEFT", 2);

		LegalCase first = loader.load(caseImport(CrimeType.THEFT), resultWith(theftContent()));
		LegalCase second = loader.load(caseImport(CrimeType.THEFT), resultWith(theftContent()));

		assertThat(castOf(first.getId())).containsEntry("defense", expected[0]);
		assertThat(castOf(second.getId())).containsEntry("defense", expected[1]);
		assertThat(castOf(second.getId()).get("prosecutor")).isNotEqualTo(castOf(second.getId()).get("defense"));
	}

	@Test
	@DisplayName("같은 범죄 유형이 동시에 적재돼도 차례로 적재한 것과 같은 배역이 나온다 — 둘이 같은 배정을 받지 않는다 (BE-125)")
	void load_concurrentSameCrimeType() throws Exception {
		String[] expected = expectedSequence("THEFT", 2);
		List<PipelineResult> results = List.of(resultWith(theftContent()), resultWith(theftContent()));
		List<CaseImport> imports = List.of(caseImport(CrimeType.THEFT), caseImport(CrimeType.THEFT));

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<LegalCase>> futures = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				int index = i;
				futures.add(pool.submit(() -> {
					start.await();
					return loader.load(imports.get(index), results.get(index));
				}));
			}
			start.countDown();
			List<String> defenses = new ArrayList<>();
			for (Future<LegalCase> future : futures) {
				defenses.add(castOf(future.get(30, TimeUnit.SECONDS).getId()).get("defense"));
			}
			assertThat(defenses).containsExactlyInAnyOrder(expected);
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	@DisplayName("대본의 배역을 쓰는 적재도 그 범죄 유형의 배정 잠금을 기다린다 — 규칙으로 배정하는 적재와 같은 잠금이다 (BE-125)")
	void load_scriptCastWaitsForCastLock() throws Exception {
		PipelineResult withScript = resultOf(theftContent(), node("""
			{"evidence": [{"factorId": 7, "role": "prosecutor", "character": "nambul", "text": "계획적이었습니다."}],
			 "cast": {"prosecutor": "nambul", "defense": "naero"},
			 "model": "openai:test", "publishable": true}"""), null);
		CaseImport theft = caseImport(CrimeType.THEFT);

		ExecutorService pool = Executors.newSingleThreadExecutor();
		try (java.sql.Connection holder = jdbcTemplate.getDataSource().getConnection()) {
			holder.setAutoCommit(false);
			try (java.sql.Statement lock = holder.createStatement()) {
				lock.execute("SELECT pg_advisory_xact_lock(hashtext('legal_case_cast:THEFT'))");
			}
			Future<LegalCase> loading = pool.submit(() -> loader.load(theft, withScript));

			assertThatThrownBy(() -> loading.get(1500, TimeUnit.MILLISECONDS))
				.isInstanceOf(java.util.concurrent.TimeoutException.class);
			holder.commit();
			assertThat(castOf(loading.get(30, TimeUnit.SECONDS).getId()))
				.isEqualTo(Map.of("prosecutor", "nambul", "defense", "naero"));
		} finally {
			pool.shutdownNow();
		}
	}

	/** 지금 DB 상태에서 그 유형에 n건을 차례로 배정하면 나올 피고인 역 (규칙 자체는 CaseCastAssignerTest가 본다) */
	private String[] expectedSequence(String crimeType, int n) {
		long naero = 0;
		long nambul = 0;
		for (Map<String, Object> row : jdbcTemplate.queryForList("""
			SELECT cast_roles->>'defense' AS defense, count(*) AS cnt FROM legal_case
			WHERE crime_type = ? AND cast_roles IS NOT NULL GROUP BY 1""", crimeType)) {
			long cnt = ((Number) row.get("cnt")).longValue();
			if ("naero".equals(row.get("defense"))) {
				naero = cnt;
			} else if ("nambul".equals(row.get("defense"))) {
				nambul = cnt;
			}
		}
		List<String> last = jdbcTemplate.queryForList("""
			SELECT cast_roles->>'defense' FROM legal_case WHERE crime_type = ? AND cast_roles IS NOT NULL
			ORDER BY id DESC LIMIT 1""", String.class, crimeType);
		String previous = last.isEmpty() ? null : last.get(0);
		String[] sequence = new String[n];
		for (int i = 0; i < n; i++) {
			previous = CaseCastAssigner.nextDefense(naero, nambul, previous);
			sequence[i] = previous;
			if ("naero".equals(previous)) {
				naero++;
			} else {
				nambul++;
			}
		}
		return sequence;
	}

	private Map<String, String> castOf(Long caseId) {
		String json = jdbcTemplate.queryForObject("SELECT cast_roles::text FROM legal_case WHERE id = ?", String.class,
			caseId);
		Map<String, String> cast = new java.util.LinkedHashMap<>();
		node(json).properties().forEach(entry -> cast.put(entry.getKey(), entry.getValue().asText()));
		return cast;
	}

	private JsonNode theftContent() {
		return node(caseJson().replace("\"crimeType\": \"MURDER\"", "\"crimeType\": \"THEFT\""));
	}

	private PipelineResult resultOf(JsonNode caseContent, JsonNode script, JsonNode classify) {
		return new PipelineResult("job", caseContent, node(reportJson()), node(courtDraftJson()),
			node(aiJudgmentJson()),
			new PipelineResult.Selection("gemini:test", 1, "consensus", "합의", List.of()), null, script, classify);
	}

	private PipelineResult result() {
		return resultWith(node(caseJson()));
	}

	private PipelineResult resultWith(JsonNode caseContent) {
		return new PipelineResult("job", caseContent, node(reportJson()), node(courtDraftJson()),
			node(aiJudgmentJson()),
			new PipelineResult.Selection("gemini:test", 1, "consensus", "합의", List.of()), null, null, null);
	}

	private PipelineResult resultWithScript(JsonNode script) {
		return new PipelineResult("job", node(caseJson()), node(reportJson()), node(courtDraftJson()),
			node(aiJudgmentJson()),
			new PipelineResult.Selection("gemini:test", 1, "consensus", "합의", List.of()), null, script, null);
	}

	/** script 단계 산출물(BE-107). 적재는 evidence · model · cast · publishable · createdAt · stopped만 본다 */
	private static String scriptJson() {
		return """
			{"evidence": [{"factorId": 1, "role": "PROSECUTOR", "character": "나로",
			               "text": "계약서에 서명한 사실은 분명합니다.", "attempts": 1, "validation": {"warnings": []}}],
			 "cast": {"prosecutor": "나로", "defense": "남불"},
			 "model": "google:gemini-2.5-flash", "publishable": true, "stopped": false,
			 "balanceWarning": null, "createdAt": "2026-10-27T09:00:00+09:00"}""";
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
