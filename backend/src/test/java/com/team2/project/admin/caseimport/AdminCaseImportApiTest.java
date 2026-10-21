package com.team2.project.admin.caseimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.pipeline.PipelineClient;
import com.team2.project.pipeline.PipelineJob;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 사건 등록 A-14 · A-15 (API 명세 8-2, BE-93). 실제 로그인 세션 + CSRF 토큰.
 *
 * <p>파이프라인 서비스는 가짜로 바꾼다 — 실제로 부르면 외부 LLM 호출이 일어나고 수십 분이 걸린다.
 * 서비스 호출 자체는 {@code HttpPipelineClientTest}가 MockRestServiceServer로 따로 본다.
 */
class AdminCaseImportApiTest extends ApiIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";
	private static final String CSRF_COOKIE = "XSRF-TOKEN";
	private static final String URL = "/api/v1/admin/case-imports";

	@Autowired AdminAccountRepository accountRepository;
	@Autowired PasswordEncoder passwordEncoder;

	/**
	 * 가짜 파이프라인 클라이언트 (TestPipelineClientConfig).
	 *
	 * <p>{@code @MockitoBean}을 쓰지 않는 이유는 그 설정 클래스에 적어 두었다 — 컨텍스트가 하나 더
	 * 생겨 CI의 DB 커넥션 상한을 넘긴다. 대신 여러 테스트가 같은 가짜를 나눠 쓰므로 매번 초기화한다.
	 */
	@Autowired PipelineClient pipelineClient;

	private AdminAccount admin;
	private MockHttpSession session;
	private Cookie csrf;

	@BeforeEach
	void setUp() throws Exception {
		admin = accountRepository.save(AdminAccount.withPassword("import-" + UUID.randomUUID() + "@example.com",
			passwordEncoder.encode(PASSWORD), "등록 테스트"));
		login();
		Mockito.reset(pipelineClient);	// 가짜를 여러 테스트가 나눠 쓴다 (TestPipelineClientConfig)
		when(pipelineClient.enabled()).thenReturn(true);
		// 부를 때마다 새 작업 ID를 준다 — 실제 서비스와 같다. 같은 값을 두 번 주면 job_id 유니크 제약에 걸린다
		when(pipelineClient.createJob(any())).thenAnswer(invocation -> new PipelineJob(
			UUID.randomUUID().toString().replace("-", ""), "QUEUED", List.of(), null, null, null, null));
	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM case_import_source WHERE import_id IN (SELECT id FROM case_import)");
		jdbcTemplate.update("DELETE FROM case_import");
		jdbcTemplate.update("DELETE FROM admin_audit_log WHERE actor_email = ?", admin.getEmail());
		accountRepository.deleteById(admin.getId());
	}

	@Test
	@DisplayName("A-14: 등록을 시작하면 202와 importId를 주고, 원문은 감사 로그에 남기지 않는다")
	void startImport_accepted() throws Exception {
		perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("2099고합001")))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.importId").isNumber())
			.andExpect(jsonPath("$.status").value("QUEUED"));

		assertThat(jdbcTemplate.queryForObject(
			"SELECT detail::text FROM admin_audit_log WHERE actor_email = ? AND action = 'CASE_IMPORT'",
			String.class, admin.getEmail()))
			.contains("2099고합001", "FIRST", "MURDER")
			.doesNotContain("판결문 전문입니다");
		// 원문은 적재할 때까지 백엔드가 들고 있다 (파이프라인이 돌려주지 않기 때문, FR-5-3)
		assertThat(jdbcTemplate.queryForObject(
			"SELECT original_text FROM case_import_source WHERE case_number = '2099고합001'", String.class))
			.isEqualTo("판결문 전문입니다");
	}

	@Test
	@DisplayName("A-14: 진행 중인 등록이 정말 도는 중이면 409 IMPORT_IN_PROGRESS")
	void startImport_inProgress() throws Exception {
		perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("2099고합001")))
			.andExpect(status().isAccepted());
		when(pipelineClient.getJob(any()))
			.thenReturn(new PipelineJob("job", "RUNNING", List.of(), null, null, null, null));

		perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("2099고합002")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IMPORT_IN_PROGRESS"));
	}

	@Test
	@DisplayName("A-14: 화면을 닫아 RUNNING으로 남은 등록이 실제로 끝났으면 새 등록을 받는다 (영원히 잠기지 않게)")
	void startImport_staleRunningRow_doesNotBlock() throws Exception {
		perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("2099고합001")))
			.andExpect(status().isAccepted());
		// 아무도 A-15를 묻지 않은 사이에 파이프라인은 끝나 있었다
		when(pipelineClient.getJob(any()))
			.thenReturn(new PipelineJob("job", "SUCCEEDED", List.of(), null, null, null, null));

		perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("2099고합002")))
			.andExpect(status().isAccepted());

		assertThat(jdbcTemplate.queryForObject(
			"SELECT status FROM case_import WHERE id = (SELECT min(id) FROM case_import)", String.class))
			.isEqualTo("SUCCEEDED");	// 앞 등록을 받아 적었다
	}

	@Test
	@DisplayName("A-14: 이미 등록한 사건번호면 409 DUPLICATE_SOURCE")
	void startImport_duplicateSource() throws Exception {
		long caseId = insertCase("MURDER", "DRAFT");
		jdbcTemplate.update("""
			INSERT INTO case_source (case_id, court_level, case_number, is_final, source_org)
			VALUES (?, 'FIRST', '2099고합777', true, '법제처')""", caseId);

		perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("2099고합777")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_SOURCE"))
			.andExpect(jsonPath("$.details[0].field").value("sources[0].caseNumber"));
	}

	@Test
	@DisplayName("A-14: 잘못된 입력은 400 VALIDATION_ERROR (범죄 유형 · 심급 · 사건번호 · 원문 · 건수)")
	void startImport_invalidBodies() throws Exception {
		List<String> bodies = List.of(
			"{}",
			"{\"crimeType\":\"NOPE\",\"sources\":[{\"courtLevel\":\"FIRST\",\"caseNumber\":\"1\",\"originalText\":\"a\"}]}",
			"{\"crimeType\":\"MURDER\",\"sources\":[]}",
			"{\"crimeType\":\"MURDER\",\"sources\":[{\"courtLevel\":\"FIRST\",\"caseNumber\":\"\",\"originalText\":\"a\"}]}",
			"{\"crimeType\":\"MURDER\",\"sources\":[{\"courtLevel\":\"FIRST\",\"caseNumber\":\"1\",\"originalText\":\"   \"}]}",
			"{\"crimeType\":\"MURDER\",\"sources\":[{\"courtLevel\":\"NOPE\",\"caseNumber\":\"1\",\"originalText\":\"a\"}]}");
		for (String body : bodies) {
			perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
	}

	@Test
	@DisplayName("A-14: 원문 20만 자 초과 · 판결문 4건은 400 (명세의 크기 제한)")
	void startImport_sizeLimits() throws Exception {
		String tooLong = """
			{"crimeType":"MURDER","sources":[{"courtLevel":"FIRST","caseNumber":"2099고합001","originalText":"%s"}]}"""
			.formatted("가".repeat(200_001));
		perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(tooLong))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		String fourSources = """
			{"crimeType":"MURDER","sources":[
			  {"courtLevel":"FIRST","caseNumber":"1","originalText":"a"},
			  {"courtLevel":"APPEAL","caseNumber":"2","originalText":"b"},
			  {"courtLevel":"SUPREME","caseNumber":"3","originalText":"c"},
			  {"courtLevel":"FIRST","caseNumber":"4","originalText":"d"}]}""";
		perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(fourSources))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	@DisplayName("거절 응답 어디에도 판결문 원문이 들어가지 않는다 (400 · 409 · 502)")
	void errorBodiesNeverContainJudgmentText() throws Exception {
		String secret = "피고인은 비밀스러운 판결문 본문입니다";
		String body = """
			{"crimeType":"MURDER","sources":[{"courtLevel":"FIRST","caseNumber":"%s","originalText":"%s"}]}""";

		// 400 (사건번호가 50자를 넘는다)
		assertNoLeak(perform(post(URL).contentType(MediaType.APPLICATION_JSON)
			.content(body.formatted("가".repeat(51), secret))).andExpect(status().isBadRequest()), secret);
		// 409 (이미 등록한 사건번호)
		long caseId = insertCase("MURDER", "DRAFT");
		jdbcTemplate.update("""
			INSERT INTO case_source (case_id, court_level, case_number, is_final, source_org)
			VALUES (?, 'FIRST', '2099고합888', true, '법제처')""", caseId);
		assertNoLeak(perform(post(URL).contentType(MediaType.APPLICATION_JSON)
			.content(body.formatted("2099고합888", secret))).andExpect(status().isConflict()), secret);
		// 502 (파이프라인이 꺼져 있다)
		when(pipelineClient.enabled()).thenReturn(false);
		assertNoLeak(perform(post(URL).contentType(MediaType.APPLICATION_JSON)
			.content(body.formatted("2099고합999", secret))).andExpect(status().isBadGateway()), secret);
	}

	private static void assertNoLeak(ResultActions result, String secret) throws Exception {
		assertThat(result.andReturn().getResponse().getContentAsString()).doesNotContain(secret);
	}

	@Test
	@DisplayName("A-14: sources에 null이 섞이면 500이 아니라 400 (Bean Validation은 null 원소를 건너뛴다)")
	void startImport_nullSourceElement() throws Exception {
		perform(post(URL).contentType(MediaType.APPLICATION_JSON)
			.content("{\"crimeType\":\"MURDER\",\"sources\":[null]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	@DisplayName("A-14: 최종 확정 판결을 둘 이상 표시하면 400 — 적재에서야 터지지 않게 미리 막는다")
	void startImport_multipleFinalDecisions() throws Exception {
		String body = """
			{"crimeType":"MURDER","sources":[
			  {"courtLevel":"FIRST","caseNumber":"2099고합001","isFinal":true,"originalText":"1심"},
			  {"courtLevel":"APPEAL","caseNumber":"2099노001","isFinal":true,"originalText":"2심"}]}""";

		perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.details[0].field").value("sources"));
		// 파이프라인을 부르지 않았으니 수십 분을 버리지 않는다
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM case_import", Integer.class)).isZero();
	}

	@Test
	@DisplayName("A-14: 같은 심급을 두 번 넣으면 400, 어느 필드인지 알려준다")
	void startImport_duplicateCourtLevel() throws Exception {
		String body = """
			{"crimeType":"MURDER","sources":[
			  {"courtLevel":"FIRST","caseNumber":"2099고합001","originalText":"본문1"},
			  {"courtLevel":"FIRST","caseNumber":"2099고합002","originalText":"본문2"}]}""";

		perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.details[0].field").value("sources"));
	}

	@Test
	@DisplayName("A-14: 파이프라인이 연결되지 않은 서버에서는 502 — 나머지 기능은 그대로 쓴다")
	void startImport_whenPipelineDisabled() throws Exception {
		when(pipelineClient.enabled()).thenReturn(false);

		perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("2099고합001")))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("IMPORT_UNAVAILABLE"));
	}

	@Test
	@DisplayName("A-15: 상태를 물으면 파이프라인 단계를 그대로 내려주고, 적재 전이라 caseId는 비어 있다")
	void getImport_returnsStages() throws Exception {
		MvcResult created = perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("2099고합001")))
			.andExpect(status().isAccepted()).andReturn();
		long importId = importIdOf(created);
		when(pipelineClient.getJob(any())).thenReturn(new PipelineJob("job", "RUNNING",
			List.of(new PipelineJob.Stage("extract", "DONE", null),
				new PipelineJob.Stage("rag", "SKIPPED", null)), null, null, null, null));

		perform(get(URL + "/" + importId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RUNNING"))
			.andExpect(jsonPath("$.stages[0].name").value("extract"))
			.andExpect(jsonPath("$.stages[0].status").value("DONE"))
			.andExpect(jsonPath("$.stages[1].status").value("SKIPPED"))
			.andExpect(jsonPath("$.caseId").isEmpty());
	}

	@Test
	@DisplayName("A-15: 없는 등록 작업은 404 IMPORT_NOT_FOUND")
	void getImport_notFound() throws Exception {
		perform(get(URL + "/999999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("IMPORT_NOT_FOUND"));
	}

	@Test
	@DisplayName("관리자 체인이 막는다: CSRF 토큰 없는 POST는 403, 로그인 없는 조회는 401")
	void security() throws Exception {
		// CSRF 검사가 인증보다 앞이라, 토큰이 없으면 로그인 여부와 무관하게 403이다
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("2099고합001")))
			.andExpect(status().isForbidden());
		mockMvc.perform(post(URL).session(session).contentType(MediaType.APPLICATION_JSON).content(body("2099고합001")))
			.andExpect(status().isForbidden());
		// 조회는 CSRF 대상이 아니라 인증에서 걸린다
		mockMvc.perform(get(URL + "/1")).andExpect(status().isUnauthorized());
	}

	// ---------------------------------------------------------------- 도우미

	private static String body(String caseNumber) {
		return """
			{"crimeType":"MURDER","sources":[{"courtLevel":"FIRST","caseNumber":"%s","courtName":"가상지방법원",
			 "decidedAt":"2025-01-10","isFinal":true,"sourceOrg":"법제처","originalText":"판결문 전문입니다"}]}"""
			.formatted(caseNumber);
	}

	private static long importIdOf(MvcResult result) throws Exception {
		String json = result.getResponse().getContentAsString();
		return Long.parseLong(json.replaceAll(".*\"importId\"\\s*:\\s*(\\d+).*", "$1"));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.session(session).cookie(csrf).header(CSRF_HEADER, csrf.getValue()));
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
		csrf = result.getResponse().getCookie(CSRF_COOKIE);
	}
}
