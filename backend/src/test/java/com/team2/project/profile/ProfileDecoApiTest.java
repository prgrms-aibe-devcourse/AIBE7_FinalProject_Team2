package com.team2.project.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 꾸미기 저장 PUT /profile-deco · 마이페이지 profileDeco · achievements · unlocked (BE-96) */
class ProfileDecoApiTest extends ApiIntegrationTest {

	private static final String DECO = "/api/v1/profile-deco";
	private static final String MY_PAGE = "/api/v1/my-page";

	/** 체험이 없는 사용자(부모 정리 대상이 아님)는 여기서 지운다 */
	private final List<UUID> bareUserIds = new ArrayList<>();

	@AfterEach
	void deleteBareUsers() {
		bareUserIds.forEach(id -> {
			jdbcTemplate.update("DELETE FROM profile_deco WHERE anonymous_user_id = ?", id);
			jdbcTemplate.update("DELETE FROM anonymous_user WHERE id = ?", id);
		});
		bareUserIds.clear();
	}

	private UUID newUser() {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO anonymous_user (id) VALUES (?)", id);
		bareUserIds.add(id);
		return id;
	}

	private static Map<String, String> defaults() {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("portrait", "/images/characters/standing/naero.webp");
		body.put("frame", "basic");
		body.put("background", "cream");
		body.put("sashStyle", "none");
		body.put("sashColorShort", "wine");
		body.put("sashColorLong", "wine");
		body.put("sashIconLeft", "none");
		body.put("sashIconRight", "none");
		body.put("bookmarkCharacter", "naero");
		body.put("bookmarkFace", "happy");
		body.put("bookmarkFaceStyle", "generated");
		body.put("bookmarkColor", "bronze");
		return body;
	}

	private static String json(Map<String, String> body) {
		return body.entrySet().stream().map(e -> "\"" + e.getKey() + "\":\"" + e.getValue().replace("\\", "\\\\") + "\"")
			.collect(Collectors.joining(",", "{", "}"));
	}

	private ResultActions putDeco(Cookie cookie, Map<String, String> body) throws Exception {
		var request = put(DECO).contentType(MediaType.APPLICATION_JSON).content(json(body));
		return mockMvc.perform(cookie == null ? request : request.cookie(cookie));
	}

	private int countDeco(UUID id) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM profile_deco WHERE anonymous_user_id = ?", Integer.class, id);
	}

	@Test
	@DisplayName("빈 마이페이지: 기본 꾸미기 · 도전과제 8개(수집은 PENDING) · 처음부터 열린 것")
	void emptyMyPage() throws Exception {
		mockMvc.perform(get(MY_PAGE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profileDeco.portrait").value("/images/characters/standing/naero.webp"))
			.andExpect(jsonPath("$.achievements.length()").value(8))
			.andExpect(jsonPath("$.achievements[0].id").value("first-case"))
			.andExpect(jsonPath("$.achievements[0].stat").value("completed"))
			.andExpect(jsonPath("$.achievements[0].status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.achievements[3].id").value("collect-5"))
			.andExpect(jsonPath("$.achievements[3].status").value("PENDING"))
			.andExpect(jsonPath("$.achievements[3].value").isEmpty())
			.andExpect(jsonPath("$.achievements[3].rewards.backgrounds[0]").value("news"))
			.andExpect(jsonPath("$.unlocked.frames.length()").value(1))
			.andExpect(jsonPath("$.unlocked.frames[0]").value("basic"));
	}

	@Test
	@DisplayName("열린 것만 고르면 저장되고(200) 마이페이지에 그대로 나온다")
	void saveAndReadBack() throws Exception {
		UUID id = newUser();
		Cookie cookie = new Cookie(COOKIE, id.toString());
		Map<String, String> body = defaults();
		body.put("bookmarkCharacter", "law-gavel");
		body.put("bookmarkColor", "green");
		body.put("sashStyle", "short");
		body.put("sashIconLeft", "star");

		putDeco(cookie, body).andExpect(status().isOk()).andExpect(jsonPath("$.bookmarkCharacter").value("law-gavel"));

		mockMvc.perform(get(MY_PAGE).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profileDeco.bookmarkCharacter").value("law-gavel"))
			.andExpect(jsonPath("$.profileDeco.sashIconLeft").value("star"));

		body.put("frame", "basic");
		body.put("bookmarkColor", "gold");
		putDeco(cookie, body).andExpect(status().isOk());	// 두 번째 저장은 같은 줄을 바꾼다
		assertThat(countDeco(id)).isEqualTo(1);
	}

	@Test
	@DisplayName("잠긴 항목은 409 DECO_LOCKED + details, 아무것도 저장하지 않는다")
	void lockedRejected() throws Exception {
		UUID id = newUser();
		Map<String, String> body = defaults();
		body.put("frame", "wood");
		body.put("portrait", "/images/cases/case-1-origin.webp");	// 수집 보상: BE-83 전까지 잠김

		putDeco(new Cookie(COOKIE, id.toString()), body)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DECO_LOCKED"))
			.andExpect(jsonPath("$.details[?(@.field == 'frame')].reason").value("DECO_LOCKED"))
			.andExpect(jsonPath("$.details[?(@.field == 'portrait')].reason").value("DECO_LOCKED"));

		assertThat(countDeco(id)).isZero();
	}

	@Test
	@DisplayName("첫 판결을 마치면 그 보상(원목 테두리 · 장면 컷 · 긴 띠 와인)을 저장할 수 있다")
	void firstCaseUnlocks() throws Exception {
		UUID id = UUID.randomUUID();
		insertExperience(id, insertCase("MURDER", "PUBLISHED"), 1, "COMPLETED");	// 부모가 사건 · 체험 · 사용자를 지운다
		Cookie cookie = new Cookie(COOKIE, id.toString());
		Map<String, String> body = defaults();
		body.put("frame", "wood");
		body.put("portrait", "/images/characters/scene/naero_idle-neutral.webp");
		body.put("sashStyle", "long");

		putDeco(cookie, body).andExpect(status().isOk());

		mockMvc.perform(get(MY_PAGE).cookie(cookie))
			.andExpect(jsonPath("$.achievements[0].status").value("CLEARED"))
			.andExpect(jsonPath("$.achievements[0].value").value(1))
			.andExpect(jsonPath("$.profileDeco.frame").value("wood"));
	}

	@Test
	@DisplayName("대표 이미지 형식 · 목록에 없는 값은 400 VALIDATION_ERROR, 아무것도 저장하지 않는다")
	void invalidRejected() throws Exception {
		UUID id = newUser();
		Cookie cookie = new Cookie(COOKIE, id.toString());
		Map<String, String> body = defaults();
		body.put("portrait", "/images/../secret.webp");
		body.put("frame", "rainbow");

		putDeco(cookie, body)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.details[?(@.field == 'portrait')].reason").value("INVALID_FORMAT"))
			.andExpect(jsonPath("$.details[?(@.field == 'frame')].reason").value("INVALID_VALUE"));

		assertThat(countDeco(id)).isZero();
	}

	@Test
	@DisplayName("쿠키 없이 저장하면 익명 ID를 발급하고(Set-Cookie) 그 ID로 저장한다")
	void noCookieIssues() throws Exception {
		String setCookie = putDeco(null, defaults()).andExpect(status().isOk())
			.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

		assertThat(setCookie).startsWith(COOKIE + "=");
		UUID issued = UUID.fromString(setCookie.split(";")[0].substring((COOKIE + "=").length()));
		bareUserIds.add(issued);
		assertThat(countDeco(issued)).isEqualTo(1);
	}
}
