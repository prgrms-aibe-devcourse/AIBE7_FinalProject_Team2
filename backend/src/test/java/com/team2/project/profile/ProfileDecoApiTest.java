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
			jdbcTemplate.update("DELETE FROM attendance WHERE anonymous_user_id = ?", id);	// 발급 시 출석(BE-98)
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
	@DisplayName("빈 마이페이지: 기본 꾸미기 · 도전과제 8개(수집도 0부터, BE-83) · 처음부터 열린 것 · 빈 컬렉션")
	void emptyMyPage() throws Exception {
		mockMvc.perform(get(MY_PAGE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profileDeco.portrait").value("/images/characters/standing/naero.webp"))
			.andExpect(jsonPath("$.achievements.length()").value(8))
			.andExpect(jsonPath("$.achievements[0].id").value("first-case"))
			.andExpect(jsonPath("$.achievements[0].stat").value("completed"))
			.andExpect(jsonPath("$.achievements[0].status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.achievements[0].isNew").value(false))
			.andExpect(jsonPath("$.achievements[0].unlockedAt").isEmpty())
			.andExpect(jsonPath("$.achievements[3].id").value("collect-5"))
			.andExpect(jsonPath("$.achievements[3].status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.achievements[3].value").value(0))
			.andExpect(jsonPath("$.collection.collected").value(0))
			.andExpect(jsonPath("$.collection.items.length()").value(0))
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
		body.put("portrait", "/images/cases/case-1-origin.webp");	// 수집 보상: 5장을 모으기 전까지 잠김

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
		// 발급할 때 그날 출석도 함께 남긴다 (BE-98)
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attendance WHERE anonymous_user_id = ?",
			Integer.class, issued)).isEqualTo(1);
	}

	private int countTable(String table) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}

	@Test
	@DisplayName("쿠키 없이 저장했다가 거절(400 · 409)되면 발급한 익명 사용자 · 출석이 함께 취소되고 쿠키도 내려가지 않는다 (BE-98)")
	void rejectedSaveRollsBackIssue() throws Exception {
		int users = countTable("anonymous_user");
		int attendance = countTable("attendance");
		Map<String, String> locked = defaults();
		locked.put("frame", "wood");	// 첫 판결 전 잠김
		Map<String, String> invalid = defaults();
		invalid.put("frame", "rainbow");	// 목록에 없는 값

		putDeco(null, locked).andExpect(status().isConflict())
			.andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull());
		putDeco(null, invalid).andExpect(status().isBadRequest())
			.andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull());

		assertThat(countTable("anonymous_user")).isEqualTo(users);
		assertThat(countTable("attendance")).isEqualTo(attendance);
	}

	private void insertSavedDeco(UUID id, String frame, String sashStyle, String sashColorShort) {
		jdbcTemplate.update("""
			INSERT INTO profile_deco (anonymous_user_id, portrait, frame, background, sash_style, sash_color_short,
			  sash_color_long, sash_icon_left, sash_icon_right, bookmark_character, bookmark_face, bookmark_face_style, bookmark_color)
			VALUES (?, '/images/characters/standing/naero.webp', ?, 'cream', ?, ?, 'wine', 'none', 'none',
			  'naero', 'happy', 'generated', 'bronze')
			""", id, frame, sashStyle, sashColorShort);
	}

	@Test
	@DisplayName("저장해 둔 값이 잠겨도(표 변경) 다른 칸 저장은 통과하고, 새로 바꾼 잠긴 칸만 409 (BE-98)")
	void savedLockedValueDoesNotBlock() throws Exception {
		UUID id = newUser();
		insertSavedDeco(id, "gold", "none", "wine");	// 표가 바뀌어 잠긴 테두리를 저장해 둔 상황
		Cookie cookie = new Cookie(COOKIE, id.toString());
		Map<String, String> body = defaults();
		body.put("frame", "gold");
		body.put("bookmarkColor", "navy");

		putDeco(cookie, body).andExpect(status().isOk());

		body.put("frame", "navy");	// 저장값과 다른 잠긴 테두리
		putDeco(cookie, body)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.details[0].field").value("frame"));
	}

	@Test
	@DisplayName("안 쓰이던 칸에 넣어 둔 잠긴 값은 쓰이게 바꿀 때 409 (BE-98)")
	void inactiveSavedValueRechecked() throws Exception {
		UUID id = newUser();
		insertSavedDeco(id, "basic", "none", "gold");	// 책갈피 없음 + 짧은 책갈피 색 gold(잠김)
		Map<String, String> body = defaults();
		body.put("sashStyle", "short");
		body.put("sashColorShort", "gold");

		putDeco(new Cookie(COOKIE, id.toString()), body)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.details[0].field").value("sashColorShort"));
	}
}
