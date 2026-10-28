package com.team2.project.admin.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * 사건 내용 수정 · 가치관 축 확정 · 사건 공개 · 공개 취소 A-11 ~ A-13 · A-13-14 (API 명세 8-2, BE-43 · BE-118).
 * 실제 로그인 세션 + CSRF 토큰.
 */
class AdminCaseEditApiTest extends ApiIntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";
	private static final String CSRF_COOKIE = "XSRF-TOKEN";

	@Autowired AdminAccountRepository accountRepository;
	@Autowired PasswordEncoder passwordEncoder;

	private AdminAccount admin;
	private MockHttpSession session;
	private Cookie csrf;
	private long caseId;
	private long overviewFactorId;

	@BeforeEach
	void setUp() throws Exception {
		admin = accountRepository.save(AdminAccount.withPassword("edit-" + UUID.randomUUID() + "@example.com",
			passwordEncoder.encode(PASSWORD), "수정 테스트"));
		login();
		caseId = insertCase("MURDER", "DRAFT");
		overviewFactorId = insertFactor(caseId, "OVERVIEW", "다툼 끝에 일어났다", "다투다 일어났다", 1);
		jdbcTemplate.update("UPDATE legal_case SET recommended_min_months = 120, recommended_max_months = 192 WHERE id = ?",
			caseId);
	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM penalty_rule WHERE case_id = ?", caseId);
		jdbcTemplate.update("DELETE FROM admin_audit_log WHERE actor_email = ?", admin.getEmail());
		accountRepository.deleteById(admin.getId());
	}

	// --- A-11 ---

	@Test
	@DisplayName("A-11: 보낸 필드만 바꾸고 null은 지운다, 사건 DRAFT → REVIEW, 감사 로그에는 바꾼 필드 이름만")
	void edit_partialAndNull() throws Exception {
		jdbcTemplate.update("UPDATE legal_case SET thumbnail_url = '/old.webp', difficulty = 'MID' WHERE id = ?", caseId);
		perform(patch(caseUrl()).contentType(MediaType.APPLICATION_JSON).content("""
				{"shortIntro": "고친 소개", "keywords": ["금전 갈등", "지인"], "thumbnailUrl": null, "estimatedMinutes": "15"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REVIEW"))
			.andExpect(jsonPath("$.content.shortIntro").value("고친 소개"))
			.andExpect(jsonPath("$.content.keywords[1]").value("지인"))
			.andExpect(jsonPath("$.content.thumbnailUrl").isEmpty())
			.andExpect(jsonPath("$.content.estimatedMinutes").value(15))
			.andExpect(jsonPath("$.content.difficulty").value("MID"))		// 보내지 않은 필드는 그대로
			.andExpect(jsonPath("$.content.overview").value("테스트 개요"));
		assertThat(jdbcTemplate.queryForObject("""
			SELECT detail->>'fields' FROM admin_audit_log WHERE actor_email = ? AND action = 'CASE_UPDATE'""",
			String.class, admin.getEmail())).contains("shortIntro", "keywords", "thumbnailUrl", "estimatedMinutes")
			.doesNotContain("고친 소개");
	}

	@Test
	@DisplayName("A-11: 권고 하한 · 상한은 하나만 보내도 저장된 값과 비교한다")
	void edit_recommendedRange() throws Exception {
		perform(patch(caseUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"recommendedMinMonths\": 200}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.details[0].field").value("recommendedMinMonths"));
		perform(patch(caseUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"recommendedMaxMonths\": null, \"recommendedMinMonths\": 200}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.recommendedMinMonths").value(200))
			.andExpect(jsonPath("$.content.recommendedMaxMonths").isEmpty());
	}

	@Test
	@DisplayName("A-11: 규칙 위반 · 필수 필드 null · keywords null · 모르는 필드 · 빈 본문 · 소수는 400")
	void edit_invalidBodies() throws Exception {
		for (String body : List.of("{}", "{\"title\": null}", "{\"title\": \"  \"}", "{\"title\": \"" + "가".repeat(101) + "\"}",
			"{\"shortIntro\": \"" + "가".repeat(201) + "\"}", "{\"overview\": null}", "{\"keywords\": null}",
			"{\"keywords\": [\"a\", null]}", "{\"difficulty\": \"HARD\"}", "{\"estimatedMinutes\": 0}",
			"{\"estimatedMinutes\": 1.5}", "{\"chargeName\": \"상해\"}", "{\"thumbnailUrl\": \"/" + "a".repeat(300) + "\"}",
			"{\"thumbnailUrl\": \"javascript:alert(1)\"}", "{\"thumbnailUrl\": \"//evil.example/x.webp\"}",
			"{\"thumbnailUrl\": \"data:image/png;base64,AAAA\"}", "{\"thumbnailUrl\": \"images/a.webp\"}")) {
			perform(patch(caseUrl()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		assertThat(caseStatus()).isEqualTo("DRAFT");	// 아무것도 바뀌지 않았다
	}

	@Test
	@DisplayName("A-11: 문자열은 앞뒤 공백을 잘라 저장하고, 썸네일은 http(s) 주소 · 서비스 경로를 받는다")
	void edit_stripsAndThumbnail() throws Exception {
		perform(patch(caseUrl()).contentType(MediaType.APPLICATION_JSON).content("""
				{"shortIntro": "  고친 소개  ", "keywords": [" 지인 "], "thumbnailUrl": " /images/cases/a.webp "}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.shortIntro").value("고친 소개"))
			.andExpect(jsonPath("$.content.keywords[0]").value("지인"))
			.andExpect(jsonPath("$.content.thumbnailUrl").value("/images/cases/a.webp"));
		perform(patch(caseUrl()).contentType(MediaType.APPLICATION_JSON).content("""
				{"thumbnailUrl": "https://cdn.example.com/cases/a.webp"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.thumbnailUrl").value("https://cdn.example.com/cases/a.webp"));
	}

	@Test
	@DisplayName("A-11: 다른 사건과 제목이 겹치면 409 DUPLICATE_TITLE, 공개한 사건은 409 CASE_PUBLISHED, 없는 사건은 404")
	void edit_conflicts() throws Exception {
		String otherTitle = jdbcTemplate.queryForObject("SELECT title FROM legal_case WHERE id = ?", String.class,
			insertCase("FRAUD", "DRAFT"));
		for (String title : List.of(otherTitle, " " + otherTitle + " ")) {	// 앞뒤 공백으로 중복 검사를 피하지 못한다
			perform(patch(caseUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"" + title + "\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_TITLE"));
		}

		jdbcTemplate.update("UPDATE legal_case SET status = 'PUBLISHED', published_at = now() WHERE id = ?", caseId);
		perform(patch(caseUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"shortIntro\": \"고침\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.details[0].reason").value("CASE_PUBLISHED"));

		perform(patch("/api/v1/admin/cases/-1").contentType(MediaType.APPLICATION_JSON).content("{\"shortIntro\": \"고침\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
	}

	// --- A-12 ---

	@Test
	@DisplayName("A-12: 축을 확정하면 CONFIRMED, null도 확정으로 본다. 공개한 사건에서도 된다")
	void confirmValueAxis() throws Exception {
		perform(put(axisUrl(overviewFactorId)).contentType(MediaType.APPLICATION_JSON).content("{\"valueAxis\": \"PRINCIPLE_RELATION\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.factorId").value(overviewFactorId))
			.andExpect(jsonPath("$.valueAxis").value("PRINCIPLE_RELATION"))
			.andExpect(jsonPath("$.valueAxisStatus").value("CONFIRMED"));
		assertThat(caseStatus()).isEqualTo("REVIEW");

		jdbcTemplate.update("UPDATE legal_case SET status = 'PUBLISHED', published_at = now() WHERE id = ?", caseId);
		perform(put(axisUrl(overviewFactorId)).contentType(MediaType.APPLICATION_JSON).content("{\"valueAxis\": null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.valueAxis").isEmpty())
			.andExpect(jsonPath("$.valueAxisStatus").value("CONFIRMED"));
		assertThat(caseStatus()).isEqualTo("PUBLISHED");
		assertThat(jdbcTemplate.queryForList("""
			SELECT action FROM admin_audit_log WHERE actor_email = ? AND action = 'VALUE_AXIS_CONFIRM'""", String.class,
			admin.getEmail())).hasSize(2);
	}

	@Test
	@DisplayName("A-12: valueAxis 키 없음 · 모르는 축은 400, 없는 요소는 404 FACTOR_NOT_FOUND")
	void confirmValueAxis_invalid() throws Exception {
		for (String body : List.of("{}", "{\"axis\": \"FAULT_STANDARD\"}", "{\"valueAxis\": \"SOMETHING\"}")) {
			perform(put(axisUrl(overviewFactorId)).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		perform(put(axisUrl(-1)).contentType(MediaType.APPLICATION_JSON).content("{\"valueAxis\": null}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("FACTOR_NOT_FOUND"));
	}

	// --- A-13 ---

	@Test
	@DisplayName("A-13: 공개 조건을 채우지 않으면 409 PUBLISH_NOT_READY와 사유 목록, 채우면 PUBLISHED · published_at · 감사 로그")
	void publish() throws Exception {
		perform(post(caseUrl() + "/publish"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PUBLISH_NOT_READY"))
			.andExpect(jsonPath("$.details.length()").value(3))
			.andExpect(jsonPath("$.details[0].reason").value("NO_PUBLISHED_AI"))
			.andExpect(jsonPath("$.details[1].reason").value("NO_PUBLISHED_COURT"))
			.andExpect(jsonPath("$.details[2].reason").value("NO_PENALTY_RULE"));

		judgment("AI");
		judgment("COURT");
		jdbcTemplate.update("""
			INSERT INTO penalty_rule (case_id, penalty_type, allowed_min, allowed_max, suspension_allowed, display_order)
			VALUES (?, 'PRISON', 60, 360, true, 1)""", caseId);
		perform(post(caseUrl() + "/publish"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.publishReadiness.ready").value(true));
		assertThat(jdbcTemplate.queryForObject("SELECT published_at IS NOT NULL FROM legal_case WHERE id = ?", Boolean.class,
			caseId)).isTrue();
		assertThat(jdbcTemplate.queryForList("""
			SELECT action FROM admin_audit_log WHERE actor_email = ? AND action = 'CASE_PUBLISH'""", String.class,
			admin.getEmail())).hasSize(1);

		perform(post(caseUrl() + "/publish"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.details[0].reason").value("CASE_PUBLISHED"));
	}

	// --- A-13-14 ---

	@Test
	@DisplayName("A-13-14: 공개 취소하면 REVIEW · published_at 지움 · 사유 감사 로그, 사용자 목록 · 상세에서 사라지고 다시 공개할 수 있다")
	void unpublish() throws Exception {
		makePublishable();
		perform(post(caseUrl() + "/publish")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/cases/" + caseId)).andExpect(status().isOk());

		perform(post(caseUrl() + "/unpublish").contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"  권리자 요청  \"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REVIEW"))
			.andExpect(jsonPath("$.publishReadiness.ready").value(true));
		assertThat(caseStatus()).isEqualTo("REVIEW");
		assertThat(jdbcTemplate.queryForObject("SELECT published_at IS NULL FROM legal_case WHERE id = ?", Boolean.class,
			caseId)).isTrue();
		assertThat(jdbcTemplate.queryForObject("""
			SELECT detail->>'reason' FROM admin_audit_log WHERE actor_email = ? AND action = 'CASE_UNPUBLISH'
			  AND target_type = 'CASE' AND target_id = ?""", String.class, admin.getEmail(), caseId)).isEqualTo("권리자 요청");
		// 판결은 그대로 공개 판결로 남는다(행을 지우지 않는다)
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM judgment WHERE case_id = ? AND is_published", Long.class,
			caseId)).isEqualTo(2);

		// 사용자 API는 PUBLISHED만 읽는다
		mockMvc.perform(get("/api/v1/cases/" + caseId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/cases"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cases[?(@.caseId == %d)]".formatted(caseId)).isEmpty());

		// 고친 뒤 다시 공개할 수 있다
		perform(patch(caseUrl()).contentType(MediaType.APPLICATION_JSON).content("{\"shortIntro\": \"고친 소개\"}"))
			.andExpect(status().isOk());
		perform(post(caseUrl() + "/publish"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
		assertThat(jdbcTemplate.queryForObject("SELECT published_at IS NOT NULL FROM legal_case WHERE id = ?", Boolean.class,
			caseId)).isTrue();	// 다시 공개하면 새 시각으로 채워진다
		mockMvc.perform(get("/api/v1/cases/" + caseId)).andExpect(status().isOk());
	}

	@Test
	@DisplayName("A-13-14: 이미 끝낸 체험도 공개 취소 뒤에는 내 체험 · 비교 결과가 404 CASE_NOT_FOUND (체험 기록은 남는다)")
	void unpublish_hidesFinishedExperience() throws Exception {
		makePublishable();
		perform(post(caseUrl() + "/publish")).andExpect(status().isOk());
		UUID anonymousId = UUID.randomUUID();
		insertExperience(anonymousId, caseId, 1, "COMPLETED");
		Cookie user = new Cookie(COOKIE, anonymousId.toString());
		mockMvc.perform(get(experienceUrl(caseId)).cookie(user)).andExpect(status().isOk());

		perform(post(caseUrl() + "/unpublish").contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"사유\"}"))
			.andExpect(status().isOk());

		for (String url : List.of(experienceUrl(caseId), experienceUrl(caseId) + "/comparison")) {
			mockMvc.perform(get(url).cookie(user))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
		}
		assertThat(countExperiences(caseId)).isEqualTo(1);	// 숨김이라 지우지 않는다
	}

	@Test
	@DisplayName("A-13-14: 공개 중이 아니면 409 CASE_NOT_PUBLISHED, 사유 없음 · 공백 · 200자 초과는 400, 없는 사건은 404")
	void unpublish_rejections() throws Exception {
		perform(post(caseUrl() + "/unpublish").contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"사유\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.currentStatus").value("DRAFT"))
			.andExpect(jsonPath("$.details[0].reason").value("CASE_NOT_PUBLISHED"));

		jdbcTemplate.update("UPDATE legal_case SET status = 'PUBLISHED', published_at = now() WHERE id = ?", caseId);
		for (String body : List.of("{}", "{\"reason\": null}", "{\"reason\": \"   \"}",
			"{\"reason\": \"" + "가".repeat(201) + "\"}")) {
			perform(post(caseUrl() + "/unpublish").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[0].field").value("reason"));
		}
		assertThat(caseStatus()).isEqualTo("PUBLISHED");	// 거절되면 아무것도 바뀌지 않는다
		perform(post(caseUrl() + "/unpublish").contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"" + "가".repeat(200) + "\"}"))
			.andExpect(status().isOk());

		perform(post("/api/v1/admin/cases/-1/unpublish").contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"사유\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
		assertThat(jdbcTemplate.queryForList("""
			SELECT action FROM admin_audit_log WHERE actor_email = ? AND action = 'CASE_UNPUBLISH'""", String.class,
			admin.getEmail())).hasSize(1);
	}

	// --- BE-103 (공개 뒤 사진 교체 절차) ---

	@Test
	@DisplayName("BE-103: 공개한 사건은 사진 업로드 · 삭제가 409 CASE_PUBLISHED, 공개 취소해야 열리고 사진을 지운 채로도 다시 공개된다")
	void publishedCase_photoChangeNeedsUnpublish() throws Exception {
		makePublishable();
		insertIncidentPhoto(caseId, "ORIGIN", "https://cdn.example.com/origin.jpg", "발단");
		jdbcTemplate.update("UPDATE legal_case SET thumbnail_url = '/images/cases/origin.webp' WHERE id = ?", caseId);
		perform(post(caseUrl() + "/publish")).andExpect(status().isOk());

		// 공개 중에는 세 경로 모두 막힌다. 업로드는 파일을 S3에 올리기 전에 사건 상태부터 본다
		for (AbstractMockHttpServletRequestBuilder<?> blocked : List.<AbstractMockHttpServletRequestBuilder<?>>of(
			multipart(photosUrl() + "/representative").file(imageFile()),
			multipart(photosUrl() + "/incident").file(imageFile()).param("role", "CONCLUSION"),
			delete(photosUrl() + "/incident/ORIGIN"))) {
			perform(blocked)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATE"))
				.andExpect(jsonPath("$.currentStatus").value("PUBLISHED"))
				.andExpect(jsonPath("$.details[0].reason").value("CASE_PUBLISHED"));
		}
		// 거절되면 아무것도 바뀌지 않는다 — 대표사진(thumbnail_url)도, 감사 로그도
		assertThat(countIncidentPhotos()).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT thumbnail_url FROM legal_case WHERE id = ?", String.class,
			caseId)).isEqualTo("/images/cases/origin.webp");
		assertThat(photoAuditActions()).isEmpty();

		// 공개 취소(A-13-14)하면 사진 API가 다시 열린다 — 이것이 공개 뒤 사진을 고치는 유일한 경로다
		perform(post(caseUrl() + "/unpublish").contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\": \"발단 사진 교체\"}")).andExpect(status().isOk());
		perform(delete(photosUrl() + "/incident/ORIGIN")).andExpect(status().isNoContent());
		assertThat(countIncidentPhotos()).isZero();
		assertThat(photoAuditActions()).containsExactly("CASE_PHOTO_DELETE");

		// 사진은 공개 조건(PublishReadiness)이 아니라, 사진을 지운 채로도 다시 공개된다
		perform(post(caseUrl() + "/publish"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.publishReadiness.ready").value(true));
	}

	@Test
	@DisplayName("상태를 바꾸는 요청은 CSRF 토큰이 없으면 403")
	void csrfRequired() throws Exception {
		mockMvc.perform(post(caseUrl() + "/publish").session(session)).andExpect(status().isForbidden());
		mockMvc.perform(post(caseUrl() + "/unpublish").session(session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\": \"사유\"}")).andExpect(status().isForbidden());
		mockMvc.perform(patch(caseUrl()).session(session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"shortIntro\": \"고침\"}")).andExpect(status().isForbidden());
		assertThat(caseStatus()).isEqualTo("DRAFT");
	}

	// --- 도움 함수 ---

	private ResultActions perform(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mockMvc.perform(request.session(session).cookie(csrf).header(CSRF_HEADER, csrf.getValue()));
	}

	private String caseUrl() {
		return "/api/v1/admin/cases/" + caseId;
	}

	private String photosUrl() {
		return caseUrl() + "/photos";
	}

	/** 내용은 보지 않는다 — 공개 중이면 S3에 올리기 전에 거절되므로 비어 있지만 않으면 된다 */
	private static MockMultipartFile imageFile() {
		return new MockMultipartFile("file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
	}

	private int countIncidentPhotos() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM case_incident_photo WHERE case_id = ?", Integer.class,
			caseId);
	}

	private List<String> photoAuditActions() {
		return jdbcTemplate.queryForList("""
			SELECT action FROM admin_audit_log WHERE actor_email = ? AND action LIKE 'CASE_PHOTO_%'""", String.class,
			admin.getEmail());
	}

	private static String axisUrl(long factorId) {
		return "/api/v1/admin/factors/" + factorId + "/value-axis";
	}

	/** 공개 조건(A-13)을 모두 채운다: 공개 AI · 재판부 판결, 형벌 규칙 */
	private void makePublishable() {
		judgment("AI");
		judgment("COURT");
		jdbcTemplate.update("""
			INSERT INTO penalty_rule (case_id, penalty_type, allowed_min, allowed_max, suspension_allowed, display_order)
			VALUES (?, 'PRISON', 60, 360, true, 1)""", caseId);
	}

	private void judgment(String subjectType) {
		jdbcTemplate.update("""
			INSERT INTO judgment (case_id, subject_type, timing, penalty_type, prison_months, is_published)
			VALUES (?, ?, 'FINAL', 'PRISON', 144, true)""", caseId, subjectType);
	}

	private String caseStatus() {
		return jdbcTemplate.queryForObject("SELECT status FROM legal_case WHERE id = ?", String.class, caseId);
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
