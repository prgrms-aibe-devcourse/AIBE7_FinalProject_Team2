package com.team2.project.admin.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.support.ApiIntegrationTest;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 사건 대본 검수 A-13-10 · A-13-11 · A-13-12 (BE-108). 판결 후보 검수(A-8 ~ A-10)와 같은 방식으로 실제 로그인
 * 세션 + CSRF 토큰으로 부른다. 대본은 적재(A-14-1)에서 들어오므로 여기서는 표에 직접 넣어 시작한다.
 */
class AdminCaseScriptApiTest extends ApiIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";
	private static final String CSRF_COOKIE = "XSRF-TOKEN";
	private static final JsonMapper MAPPER = JsonMapper.builder().build();	// 본문에 줄바꿈이 들어가 JSON으로 만든다

	// 셋째 줄의 factorId 9는 사건에 없는 요소다 — label을 못 찾아도 응답이 깨지지 않는지 본다.
	// 둘째 줄은 끝내 생성하지 못한 줄이라 text가 null이다(Map.copyOf를 쓰면 NPE가 나는 자리)
	private static final String EVIDENCE = """
		[{"factorId": 1, "role": "PROSECUTOR", "character": "나로", "text": "계약서에 서명한 사실은 분명합니다.",
		  "attempts": 1, "validation": {"warnings": []}},
		 {"factorId": 2, "role": "DEFENSE", "character": "남불", "text": null,
		  "attempts": 3, "validation": {"warnings": ["증거 카드에 없는 사실을 말했습니다"]}},
		 {"factorId": 9, "role": "PROSECUTOR", "character": "나로", "text": "지워진 요소에 붙은 대사입니다.",
		  "attempts": 1, "validation": {"warnings": []}}]""";
	private static final String CAST = "{\"prosecutor\": \"나로\", \"defense\": \"남불\"}";

	@Autowired AdminAccountRepository accountRepository;
	@Autowired PasswordEncoder passwordEncoder;

	private AdminAccount admin;
	private MockHttpSession session;
	private Cookie csrf;
	private long caseId;

	@BeforeEach
	void setUp() throws Exception {
		admin = accountRepository.save(AdminAccount.withPassword("script-" + UUID.randomUUID() + "@example.com",
			passwordEncoder.encode(PASSWORD), "대본 검수 테스트"));
		login();
		caseId = insertCase("MURDER", "DRAFT");
		insertFactor(caseId, "OVERVIEW", "계약서에 서명했다", "서명했다", 1);
		insertFactor(caseId, "DETAIL", "돈을 돌려주지 않았다", null, 2);
	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM admin_audit_log WHERE actor_email = ?", admin.getEmail());
		accountRepository.deleteById(admin.getId());
	}

	@Test
	@DisplayName("A-13-10: 대사마다 판단 요소 문구(label)를 붙여 주고, 배역 · 생성 정보 · 검수 대기를 함께 준다")
	void getScript_addsFactorLabels() throws Exception {
		insertScript(true, false);

		perform(get(scriptUrl()))
			.andExpect(status().isOk())
			// 파이프라인 factorId는 factor.display_order다(BE-93 2차) — 그 번호로 요소 문구를 찾아 붙인다
			.andExpect(jsonPath("$.evidence[0].label").value("계약서에 서명했다"))
			.andExpect(jsonPath("$.evidence[0].role").value("PROSECUTOR"))
			.andExpect(jsonPath("$.evidence[0].character").value("나로"))
			.andExpect(jsonPath("$.evidence[0].attempts").value(1))
			.andExpect(jsonPath("$.evidence[1].label").value("돈을 돌려주지 않았다"))
			.andExpect(jsonPath("$.evidence[1].text").value(nullValue()))	// 끝내 생성하지 못한 줄
			.andExpect(jsonPath("$.evidence[1].validation.warnings[0]").value("증거 카드에 없는 사실을 말했습니다"))
			.andExpect(jsonPath("$.evidence[2].label").value(nullValue()))	// 사건에 없는 요소면 비운다
			.andExpect(jsonPath("$.evidence[2].text").value("지워진 요소에 붙은 대사입니다."))
			.andExpect(jsonPath("$.cast.prosecutor").value("나로"))
			.andExpect(jsonPath("$.cast.defense").value("남불"))
			.andExpect(jsonPath("$.model").value("google:gemini-2.5-flash"))
			.andExpect(jsonPath("$.publishable").value(true))
			.andExpect(jsonPath("$.stopped").value(false))
			.andExpect(jsonPath("$.generatedAt").exists())
			.andExpect(jsonPath("$.updatedAt").exists())
			// 검수 칸은 A-8과 같은 이름 · 같은 형식
			.andExpect(jsonPath("$.reviewStatus").value("PENDING"))
			.andExpect(jsonPath("$.isPublished").value(false))
			.andExpect(jsonPath("$.reviewedBy").value(nullValue()))
			.andExpect(jsonPath("$.reviewNote").value(nullValue()));
	}

	@Test
	@DisplayName("A-13-11: 승인하면 공개 대본이 되고 검수자 이메일 · 감사 로그가 남는다. 사건 공개 여부는 건드리지 않는다")
	void approve_publishesScript() throws Exception {
		insertScript(true, false);

		perform(post(scriptUrl() + "/approve"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
			.andExpect(jsonPath("$.isPublished").value(true))
			.andExpect(jsonPath("$.reviewedBy").value(admin.getEmail()))
			.andExpect(jsonPath("$.reviewedAt").exists());

		assertThat(reviewStatus()).isEqualTo("APPROVED");
		assertThat(caseStatus()).isEqualTo("DRAFT");	// 대본 검수는 사건 상태를 바꾸지 않는다(판결 후보와 다른 점)
		assertThat(auditActions()).containsExactly("CASE_SCRIPT_APPROVE");
	}

	@Test
	@DisplayName("A-13-11: 파이프라인이 어긋난다고 표시한 대본(publishable false)은 409 — 다시 생성해야 한다")
	void approve_notPublishable_isConflict() throws Exception {
		insertScript(false, true);

		perform(post(scriptUrl() + "/approve"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));

		assertThat(reviewStatus()).isNull();	// 검수 기록이 생기지 않는다
		assertThat(auditActions()).isEmpty();	// 막힌 요청은 감사 로그도 남기지 않는다
	}

	@Test
	@DisplayName("A-13-12: 반려하면 사유 · 검수자가 남고 공개되지 않는다. 사유 빈 값 · 500자 초과는 400")
	void reject_recordsReason() throws Exception {
		insertScript(true, false);

		for (String body : List.of("{}", "{\"reason\":\"  \"}", "{\"reason\":\"" + "가".repeat(501) + "\"}")) {
			perform(post(scriptUrl() + "/reject").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}

		perform(post(scriptUrl() + "/reject").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"  검사 쪽 대사가 너무 깁니다.  \"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewStatus").value("REJECTED"))
			.andExpect(jsonPath("$.isPublished").value(false))
			.andExpect(jsonPath("$.reviewedBy").value(admin.getEmail()))
			.andExpect(jsonPath("$.reviewNote").value("검사 쪽 대사가 너무 깁니다."));	// 앞뒤 공백은 잘라서 저장

		assertThat(auditActions()).containsExactly("CASE_SCRIPT_REJECT");
	}

	@Test
	@DisplayName("승인한 대본도 반려로 내릴 수 있다 (판결 후보와 달리 교체 대상이 하나뿐이라 막지 않는다)")
	void reject_afterApprove_takesItDown() throws Exception {
		insertScript(true, false);
		perform(post(scriptUrl() + "/approve")).andExpect(status().isOk());

		perform(post(scriptUrl() + "/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"다시 만들기\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewStatus").value("REJECTED"))
			.andExpect(jsonPath("$.isPublished").value(false));

		assertThat(auditActions()).containsExactly("CASE_SCRIPT_APPROVE", "CASE_SCRIPT_REJECT");
	}

	@Test
	@DisplayName("A-13-13: 대사를 고치면 사람이 고침으로 표시되고, 빈 대사가 차면 publishable이 다시 올라간다")
	void editLine_fillsFailedLineAndLiftsPublishable() throws Exception {
		insertScript(false, false);	// 둘째 줄(text null) 때문에 publishable이 false인 대본

		perform(patch(scriptUrl() + "/evidence/2").contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":\"  그 돈은 빌린 것이 아니라 받은 것입니다.  \"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.evidence[1].text").value("그 돈은 빌린 것이 아니라 받은 것입니다."))	// 앞뒤 공백은 자른다
			.andExpect(jsonPath("$.evidence[1].validation.manual").value(true))
			.andExpect(jsonPath("$.evidence[1].validation.ok").value(true))
			// 경고는 남긴다 — 사람이 보고 판단할 값이다
			.andExpect(jsonPath("$.evidence[1].validation.warnings[0]").value("증거 카드에 없는 사실을 말했습니다"))
			.andExpect(jsonPath("$.evidence[1].editedAt").exists())
			.andExpect(jsonPath("$.evidence[0].validation.manual").doesNotExist())	// 다른 줄은 그대로
			.andExpect(jsonPath("$.publishable").value(true));	// 빈 대사가 없어져 승인할 수 있게 된다

		assertThat(auditActions()).containsExactly("CASE_SCRIPT_UPDATE");
		// 고친 대사 본문은 감사 로그에 남기지 않는다(원문 · 모델 출력과 같은 취급)
		assertThat(auditDetails()).doesNotContain("빌린 것이 아니라").contains("factorId");

		perform(post(scriptUrl() + "/approve")).andExpect(status().isOk());	// 이제 승인된다
	}

	@Test
	@DisplayName("A-13-13: 고치면 검수 기록이 지워져 다시 검수 대기가 된다")
	void editLine_resetsReview() throws Exception {
		insertScript(true, false);
		perform(post(scriptUrl() + "/approve")).andExpect(status().isOk());

		perform(patch(scriptUrl() + "/evidence/1").contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":\"말투만 조금 고칩니다.\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewStatus").value("PENDING"))
			.andExpect(jsonPath("$.isPublished").value(false))
			.andExpect(jsonPath("$.reviewedBy").value(nullValue()))
			.andExpect(jsonPath("$.reviewedAt").value(nullValue()));

		assertThat(reviewStatus()).isNull();
	}

	@Test
	@DisplayName("A-13-13: 호출 한도로 멈춘 대본은 고쳐도 publishable이 올라가지 않는다 (없는 줄은 고칠 수 없다)")
	void editLine_stoppedStaysUnpublishable() throws Exception {
		insertScript(false, true);

		perform(patch(scriptUrl() + "/evidence/2").contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":\"사람이 채운 대사입니다.\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.evidence[1].text").value("사람이 채운 대사입니다."))
			.andExpect(jsonPath("$.stopped").value(true))
			.andExpect(jsonPath("$.publishable").value(false));	// 멈춘 대본은 줄 자체가 모자라 다시 생성해야 한다

		perform(post(scriptUrl() + "/approve")).andExpect(status().isConflict());
	}

	@Test
	@DisplayName("A-13-13: 대본에 없는 요소는 404, 빈 대사 · 100자 초과 · 3줄은 400")
	void editLine_rejectsBadInput() throws Exception {
		insertScript(true, false);

		perform(patch(scriptUrl() + "/evidence/7").contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":\"없는 요소입니다.\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SCRIPT_LINE_NOT_FOUND"));

		for (String body : List.of("{}", "{\"text\":\"  \"}", "{\"text\":\"" + "가".repeat(101) + "\"}",
				"{\"text\":\"한 줄\\n두 줄\\n세 줄\"}")) {
			perform(patch(scriptUrl() + "/evidence/1").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		assertThat(auditActions()).isEmpty();	// 막힌 요청은 감사 로그를 남기지 않는다
	}

	@Test
	@DisplayName("A-13-13: 길이는 파이프라인과 같은 방식으로 센다 — 줄바꿈은 글자 수에 넣지 않는다 (50자 + 50자는 통과)")
	void editLine_countsCharsLikePipeline() throws Exception {
		insertScript(true, false);

		// 파이프라인은 sum(len(line))으로 센다. 줄바꿈까지 세면 101자가 되어 거절되던 자리 (PR #300 리뷰 2번)
		String twoLines = "가".repeat(50) + "\n" + "나".repeat(50);
		perform(patch(scriptUrl() + "/evidence/1").contentType(MediaType.APPLICATION_JSON)
				.content(MAPPER.writeValueAsString(Map.of("text", twoLines))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.evidence[0].text").value(twoLines));

		// 글자 수 자체가 100을 넘으면(줄바꿈 빼고 101자) 거절한다
		perform(patch(scriptUrl() + "/evidence/1").contentType(MediaType.APPLICATION_JSON)
				.content(MAPPER.writeValueAsString(Map.of("text", "가".repeat(51) + "\n" + "나".repeat(50)))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	@DisplayName("승인과 대사 수정이 동시에 와도 고친 대사가 되살아나 사라지지 않는다 (사건 행 잠금으로 차례 처리)")
	void approveAndEdit_concurrent_keepsEditedLine() throws Exception {
		insertFullScript();	// 모든 줄에 대사가 있어야 한 줄을 고쳐도 publishable이 true로 남는다
		String edited = "동시에 고친 대사입니다.";
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		try {
			Callable<Integer> approve = () -> {
				ready.countDown();
				ready.await();
				return perform(post(scriptUrl() + "/approve")).andReturn().getResponse().getStatus();
			};
			Callable<Integer> edit = () -> {
				ready.countDown();
				ready.await();
				return perform(patch(scriptUrl() + "/evidence/1").contentType(MediaType.APPLICATION_JSON)
					.content(MAPPER.writeValueAsString(Map.of("text", edited)))).andReturn().getResponse().getStatus();
			};
			List<Future<Integer>> futures = List.of(pool.submit(approve), pool.submit(edit));
			for (Future<Integer> future : futures) {
				assertThat(future.get()).isEqualTo(200);
			}
		} finally {
			pool.shutdownNow();
		}

		// 어느 쪽이 먼저 들어가든 고친 대사는 남아야 한다. 잠그지 않으면 승인 쪽이 읽어 둔 옛 대사가 되살아난다
		assertThat(firstLineText()).isEqualTo(edited);
		// 수정이 나중이었다면 검수 대기로 돌아가 있고, 승인이 나중이었다면 승인 상태다 — 둘 다 "고친 대사 기준"이다
		assertThat(reviewStatus()).isIn(java.util.Arrays.asList(null, "APPROVED"));
	}

	@Test
	@DisplayName("A-6: 사건 상세에 대본 요약(exists · reviewStatus · publishable)이 붙는다. 공개 조건은 건드리지 않는다")
	void caseDetail_carriesScriptSummary() throws Exception {
		perform(get("/api/v1/admin/cases/" + caseId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.script.exists").value(false))		// 기존 사건(대본 없음)
			.andExpect(jsonPath("$.script.reviewStatus").value(nullValue()))
			.andExpect(jsonPath("$.script.publishable").value(nullValue()));

		insertScript(false, false);
		perform(get("/api/v1/admin/cases/" + caseId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.script.exists").value(true))
			.andExpect(jsonPath("$.script.reviewStatus").value("PENDING"))
			.andExpect(jsonPath("$.script.publishable").value(false))
			// 미승인 대본이 공개 조건에 끼어들지 않는다 (경고로만 쓰인다)
			.andExpect(jsonPath("$.publishReadiness.reasons", everyItem(not(containsString("SCRIPT")))));
	}

	@Test
	@DisplayName("없는 사건은 404 CASE_NOT_FOUND, 사건은 있는데 대본이 없으면 404 SCRIPT_NOT_FOUND (A-13-7과 같은 구분)")
	void notFound_separatesCaseAndScript() throws Exception {
		perform(get("/api/v1/admin/cases/-1/script"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));

		perform(get(scriptUrl()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SCRIPT_NOT_FOUND"));
		perform(post(scriptUrl() + "/approve"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SCRIPT_NOT_FOUND"));
	}

	@Test
	@DisplayName("관리자 체인이 막는다: CSRF 토큰 없는 POST는 403, 로그인 없는 조회는 401")
	void adminChainGuards() throws Exception {
		insertScript(true, false);

		mockMvc.perform(post(scriptUrl() + "/approve").session(session)).andExpect(status().isForbidden());
		mockMvc.perform(get(scriptUrl())).andExpect(status().isUnauthorized());

		assertThat(reviewStatus()).isNull();
	}

	// --- 블록 (BE-126) ---

	@Test
	@DisplayName("A-13-10: 블록마다 내용 · 판 · 생성 근거 · 검수 결과 · 고쳐 쓰기 기록을 주고, 메타 · 못 만든 단위도 준다 (BE-126)")
	void getScript_carriesBlocks() throws Exception {
		long scriptId = insertScript(true, false);
		jdbcTemplate.update("""
			UPDATE case_script SET issue = 'denied', admits = false, pending = '["U1"]'::jsonb WHERE id = ?""", scriptId);
		insertBlockRow(scriptId, "U0", """
			{"questions": [{"q": "질문", "a": "아니요"}]}""", true, """
			[{"unit": "U0", "path": "/questions/0/q", "code": "FORMAL_ENDING", "severity": "warning"}]""");

		perform(get(scriptUrl()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.blocks.length()").value(2))
			.andExpect(jsonPath("$.blocks[0].unit").value("U0"))		// U0 → U4 순서
			.andExpect(jsonPath("$.blocks[0].content.questions[0].a").value("아니요"))	// 관리자에게는 저장값 그대로
			.andExpect(jsonPath("$.blocks[0].revision").value(1))
			.andExpect(jsonPath("$.blocks[0].basis.case").value(1))
			.andExpect(jsonPath("$.blocks[0].review[0].code").value("FORMAL_ENDING"))
			.andExpect(jsonPath("$.blocks[0].repairs[0].before").value("고치기 전"))
			.andExpect(jsonPath("$.blocks[0].publishable").value(true))
			.andExpect(jsonPath("$.blocks[0].reviewStatus").value("PENDING"))
			.andExpect(jsonPath("$.blocks[1].unit").value("U3"))
			.andExpect(jsonPath("$.blocks[1].content[0].factorId").value(1))
			.andExpect(jsonPath("$.meta.issue").value("denied"))
			.andExpect(jsonPath("$.meta.admits").value(false))
			.andExpect(jsonPath("$.meta.cast.prosecutor").value("나로"))
			.andExpect(jsonPath("$.pending[0]").value("U1"))
			.andExpect(jsonPath("$.evidence[0].label").value("계약서에 서명했다"));	// 기존 필드는 U3에서 그대로
	}

	@Test
	@DisplayName("A-13-11 · 12: 통승인 · 통거절은 모든 블록에 한 번에 적용한다 (1차, BE-126)")
	void approveAndReject_applyToAllBlocks() throws Exception {
		long scriptId = insertScript(true, false);
		insertBlockRow(scriptId, "U1", "{\"lines\": [\"해설\"]}", true, "[]");
		insertBlockRow(scriptId, "U4", "{\"prosecutor\": \"의견\", \"defense\": \"의견\"}", true, "[]");

		perform(post(scriptUrl() + "/approve"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
			.andExpect(jsonPath("$.blocks[*].reviewStatus", everyItem(org.hamcrest.Matchers.is("APPROVED"))))
			.andExpect(jsonPath("$.blocks[*].reviewedBy", everyItem(org.hamcrest.Matchers.is(admin.getEmail()))));
		assertThat(blockReviewStatuses()).containsOnlyKeys("U1", "U3", "U4").allSatisfy(
			(unit, status) -> assertThat(status).isEqualTo("APPROVED"));

		perform(post(scriptUrl() + "/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"다시 만들기\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewStatus").value("REJECTED"))
			.andExpect(jsonPath("$.blocks[*].reviewNote", everyItem(org.hamcrest.Matchers.is("다시 만들기"))));
		assertThat(blockReviewStatuses().values()).containsOnly("REJECTED");
	}

	@Test
	@DisplayName("A-13-11: 오류 있는 블록 · 만들지 못한 단위가 있으면 409 — 다른 블록도 승인되지 않는다 (BE-126)")
	void approve_blockErrorOrPending_isConflict() throws Exception {
		long scriptId = insertScript(true, false);
		insertBlockRow(scriptId, "U2", "{\"questions\": []}", false, """
			[{"unit": "U2", "path": "/issues_narr/1", "code": "FORMAL_ENDING", "severity": "error"}]""");

		perform(get(scriptUrl()))
			.andExpect(jsonPath("$.publishable").value(false))
			.andExpect(jsonPath("$.blocks[0].publishable").value(false));
		perform(post(scriptUrl() + "/approve"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
		assertThat(blockReviewStatuses().values()).containsOnlyNulls();

		// 블록에 오류가 없어도 만들지 못한 단위가 있으면 대본 전체가 공개 불가다 — 파이프라인 publishable이 false로 온다
		jdbcTemplate.update("DELETE FROM case_script_block WHERE script_id = ? AND unit = 'U2'", scriptId);
		jdbcTemplate.update("UPDATE case_script SET pending = '[\"U1\"]'::jsonb, publishable = false WHERE id = ?",
			scriptId);
		perform(post(scriptUrl() + "/approve")).andExpect(status().isConflict());
		assertThat(auditActions()).isEmpty();
	}

	@Test
	@DisplayName("A-13-13: 대사를 고치면 U3 판이 오르고, 그 줄의 검수 오류가 지워지고, 모든 블록이 다시 검수 대기가 된다 (BE-126)")
	void editLine_bumpsRevisionClearsLineErrorAndResetsAllBlocks() throws Exception {
		long scriptId = insertScriptRow(true, false, null);
		insertBlockRow(scriptId, "U3", """
			[{"factorId": 1, "role": "PROSECUTOR", "character": "나로", "text": "징역 3년이 마땅합니다.",
			  "attempts": 1, "validation": {"warnings": []}},
			 {"factorId": 2, "role": "DEFENSE", "character": "남불", "text": "둘째 줄입니다.",
			  "attempts": 1, "validation": {"warnings": []}}]""", false, """
			[{"unit": "U3", "path": "/0", "code": "SENTENCING", "severity": "error", "factorId": 1},
			 {"unit": "U3", "path": "/0", "code": "LENGTH", "severity": "warning", "factorId": 1},
			 {"unit": "U3", "path": "/1", "code": "LENGTH", "severity": "warning", "factorId": 2}]""");
		insertBlockRow(scriptId, "U4", "{\"prosecutor\": \"의견\", \"defense\": \"의견\"}", true, "[]");
		jdbcTemplate.update("UPDATE case_script SET publishable = false WHERE id = ?", scriptId);
		jdbcTemplate.update("UPDATE case_script_block SET review_status = 'REJECTED', review_note = '구형' WHERE script_id = ?",
			scriptId);

		perform(patch(scriptUrl() + "/evidence/1").contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":\"계약서에 서명한 사실은 분명합니다.\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.blocks[0].unit").value("U3"))
			.andExpect(jsonPath("$.blocks[0].revision").value(2))
			.andExpect(jsonPath("$.blocks[0].review.length()").value(2))		// 그 줄의 오류만 지운다 (경고는 남긴다)
			.andExpect(jsonPath("$.blocks[0].review[*].severity", everyItem(org.hamcrest.Matchers.is("warning"))))
			.andExpect(jsonPath("$.blocks[0].publishable").value(true))
			.andExpect(jsonPath("$.blocks[1].revision").value(1))			// 다른 블록 값은 그대로
			.andExpect(jsonPath("$.publishable").value(true))
			.andExpect(jsonPath("$.reviewStatus").value("PENDING"));
		assertThat(blockReviewStatuses().values()).containsOnlyNulls();		// 반려였던 U4도 다시 대기 (통승인 1차)

		perform(post(scriptUrl() + "/approve")).andExpect(status().isOk());
	}

	// --- 도움 함수 ---

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.session(session).cookie(csrf).header(CSRF_HEADER, csrf.getValue()));
	}

	private String scriptUrl() {
		return "/api/v1/admin/cases/" + caseId + "/script";
	}

	/** 모든 줄에 대사가 있는 대본 (빈 줄이 없어 publishable이 계속 true다) */
	private void insertFullScript() {
		long scriptId = insertScriptRow(true, false, null);
		insertBlockRow(scriptId, "U3", """
			[{"factorId": 1, "role": "PROSECUTOR", "character": "나로", "text": "첫째 줄입니다.",
			  "attempts": 1, "validation": {"warnings": []}},
			 {"factorId": 2, "role": "DEFENSE", "character": "남불", "text": "둘째 줄입니다.",
			  "attempts": 1, "validation": {"warnings": []}}]""", true, "[]");
	}

	/** 적재(A-14-1)가 넣는 모양 그대로 한 건 만든다 — 증거조사만 만든 대본은 U3 블록 하나다(BE-126) */
	private long insertScript(boolean publishable, boolean stopped) {
		long scriptId = insertScriptRow(publishable, stopped, publishable ? null : "검사 쪽 대사가 변호인보다 두 배 깁니다");
		insertBlockRow(scriptId, "U3", EVIDENCE, publishable, "[]");
		return scriptId;
	}

	private long insertScriptRow(boolean publishable, boolean stopped, String balanceWarning) {
		return jdbcTemplate.queryForObject("""
			INSERT INTO case_script (case_id, model, generated_at, stopped, cast_roles, balance_warning, publishable)
			VALUES (?, 'google:gemini-2.5-flash', now() - interval '1 hour', ?, ?::jsonb, ?::text, ?)
			RETURNING id""", Long.class, caseId, stopped, CAST, balanceWarning, publishable);
	}

	private void insertBlockRow(long scriptId, String unit, String content, boolean publishable, String review) {
		jdbcTemplate.update("""
			INSERT INTO case_script_block (script_id, unit, content, publishable, review, basis, repairs)
			VALUES (?, ?, ?::jsonb, ?, ?::jsonb, '{"case": 1}'::jsonb, '[{"unit": "%s", "before": "고치기 전"}]'::jsonb)"""
			.formatted(unit), scriptId, unit, content, publishable, review);
	}

	private String firstLineText() {
		return jdbcTemplate.queryForObject("""
			SELECT b.content -> 0 ->> 'text' FROM case_script_block b JOIN case_script s ON s.id = b.script_id
			WHERE s.case_id = ? AND b.unit = 'U3'""", String.class, caseId);
	}

	/** U3 블록의 검수 상태 */
	private String reviewStatus() {
		return blockReviewStatuses().get("U3");
	}

	/** 블록마다의 검수 상태 (단위 → 상태, 대기는 null) */
	private Map<String, String> blockReviewStatuses() {
		Map<String, String> statuses = new java.util.LinkedHashMap<>();
		jdbcTemplate.query("""
			SELECT b.unit, b.review_status FROM case_script_block b JOIN case_script s ON s.id = b.script_id
			WHERE s.case_id = ? ORDER BY b.unit""",
			rs -> {
				statuses.put(rs.getString(1), rs.getString(2));
			}, caseId);
		return statuses;
	}

	private String caseStatus() {
		return jdbcTemplate.queryForObject("SELECT status FROM legal_case WHERE id = ?", String.class, caseId);
	}

	/** 감사 로그 details를 통째로 본다 — 고친 대사 본문이 새어 들어가지 않았는지 확인한다 */
	private String auditDetails() {
		return String.join(" ", jdbcTemplate.queryForList("""
			SELECT coalesce(detail::text, '') FROM admin_audit_log WHERE actor_email = ? ORDER BY id""",
			String.class, admin.getEmail()));
	}

	private List<String> auditActions() {
		return jdbcTemplate.queryForList("""
			SELECT action FROM admin_audit_log WHERE actor_email = ? AND action LIKE 'CASE_SCRIPT_%' ORDER BY id""",
			String.class, admin.getEmail());
	}

	private void login() throws Exception {
		Cookie first = mockMvc.perform(get("/api/v1/admin/auth/csrf")).andReturn().getResponse().getCookie(CSRF_COOKIE);
		MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login").cookie(first)
				.header(CSRF_HEADER, first.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(admin.getEmail(), PASSWORD)))
			.andExpect(status().isOk())
			.andReturn();
		session = (MockHttpSession) result.getRequest().getSession(false);
		csrf = result.getResponse().getCookie(CSRF_COOKIE);	// 로그인하면 CSRF 토큰이 바뀐다 (BE-67)
	}
}
