package com.team2.project.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.support.ApiIntegrationTest;
import com.team2.project.support.FakeMemberTokens;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 꾸미기 저장 PUT /profile-deco · 마이페이지 profileDeco · achievements · unlocked (BE-96) */
@Import(FakeMemberTokens.class)
class ProfileDecoApiTest extends ApiIntegrationTest {

	private static final String DECO = "/api/v1/profile-deco";
	private static final String MY_PAGE = "/api/v1/my-page";

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

	private ResultActions putDeco(Long memberId, Map<String, String> body) throws Exception {
		var request = put(DECO).contentType(MediaType.APPLICATION_JSON).content(json(body));
		return mockMvc.perform(memberId == null ? request : request.header("Authorization", bearer(memberId)));
	}

	private int countDeco(long memberId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM member_profile_deco WHERE member_id = ?", Integer.class, memberId);
	}

	private void insertCompletedExperience(long memberId, long caseId) {
		jdbcTemplate.update("INSERT INTO experience (member_id, case_id, attempt_no, status, completed_at) VALUES (?, ?, 1, 'COMPLETED', now())",
			memberId, caseId);
	}

	@Test
	@DisplayName("새 회원의 마이페이지: 기본 꾸미기 · 도전과제 8개(수집도 0부터, BE-83) · 처음부터 열린 것 · 빈 컬렉션")
	void emptyMyPage() throws Exception {
		mockMvc.perform(get(MY_PAGE).header("Authorization", bearer(registerMember())))
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
		long id = registerMember();
		Map<String, String> body = defaults();
		body.put("bookmarkCharacter", "law-gavel");
		body.put("bookmarkColor", "green");
		body.put("sashStyle", "short");
		body.put("sashIconLeft", "star");

		putDeco(id, body).andExpect(status().isOk()).andExpect(jsonPath("$.bookmarkCharacter").value("law-gavel"));

		mockMvc.perform(get(MY_PAGE).header("Authorization", bearer(id)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profileDeco.bookmarkCharacter").value("law-gavel"))
			.andExpect(jsonPath("$.profileDeco.sashIconLeft").value("star"));

		body.put("frame", "basic");
		body.put("bookmarkColor", "gold");
		putDeco(id, body).andExpect(status().isOk());	// 두 번째 저장은 같은 줄을 바꾼다
		assertThat(countDeco(id)).isEqualTo(1);
	}

	@Test
	@DisplayName("잠긴 항목은 409 DECO_LOCKED + details, 아무것도 저장하지 않는다")
	void lockedRejected() throws Exception {
		long id = registerMember();
		Map<String, String> body = defaults();
		body.put("frame", "wood");
		body.put("portrait", "/images/cases/case-1-origin.webp");	// 수집 보상: 5장을 모으기 전까지 잠김

		putDeco(id, body)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DECO_LOCKED"))
			.andExpect(jsonPath("$.details[?(@.field == 'frame')].reason").value("DECO_LOCKED"))
			.andExpect(jsonPath("$.details[?(@.field == 'portrait')].reason").value("DECO_LOCKED"));

		assertThat(countDeco(id)).isZero();
	}

	@Test
	@DisplayName("첫 판결을 마치면 그 보상(원목 테두리 · 장면 컷 · 긴 띠 와인)을 저장할 수 있다")
	void firstCaseUnlocks() throws Exception {
		long id = registerMember();
		insertCompletedExperience(id, insertCase("MURDER", "PUBLISHED"));	// 부모가 사건 · 체험을 지운다
		Map<String, String> body = defaults();
		body.put("frame", "wood");
		body.put("portrait", "/images/characters/scene/naero_idle-neutral.webp");
		body.put("sashStyle", "long");

		putDeco(id, body).andExpect(status().isOk());

		mockMvc.perform(get(MY_PAGE).header("Authorization", bearer(id)))
			.andExpect(jsonPath("$.achievements[0].status").value("CLEARED"))
			.andExpect(jsonPath("$.achievements[0].value").value(1))
			.andExpect(jsonPath("$.profileDeco.frame").value("wood"));
	}

	@Test
	@DisplayName("대표 이미지 형식 · 목록에 없는 값은 400 VALIDATION_ERROR, 아무것도 저장하지 않는다")
	void invalidRejected() throws Exception {
		long id = registerMember();
		Map<String, String> body = defaults();
		body.put("portrait", "/images/../secret.webp");
		body.put("frame", "rainbow");

		putDeco(id, body)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.details[?(@.field == 'portrait')].reason").value("INVALID_FORMAT"))
			.andExpect(jsonPath("$.details[?(@.field == 'frame')].reason").value("INVALID_VALUE"));

		assertThat(countDeco(id)).isZero();
	}

	@Test
	@DisplayName("비회원은 저장도 마이페이지도 401 UNAUTHORIZED이고 아무것도 만들지 않으며 쿠키도 내리지 않는다")
	void guestUnauthorized() throws Exception {
		int before = jdbcTemplate.queryForObject("SELECT count(*) FROM member_profile_deco", Integer.class);
		int users = jdbcTemplate.queryForObject("SELECT count(*) FROM anonymous_user", Integer.class);

		putDeco(null, defaults()).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull());
		mockMvc.perform(get(MY_PAGE)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member_profile_deco", Integer.class)).isEqualTo(before);
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM anonymous_user", Integer.class)).isEqualTo(users);
	}

	@Test
	@DisplayName("다른 회원의 꾸미기는 내 마이페이지에 보이지 않는다")
	void otherMemberIsolated() throws Exception {
		long a = registerMember();
		long b = registerMember();
		Map<String, String> body = defaults();
		body.put("bookmarkCharacter", "law-gavel");
		putDeco(a, body).andExpect(status().isOk());

		mockMvc.perform(get(MY_PAGE).header("Authorization", bearer(b)))
			.andExpect(jsonPath("$.profileDeco.bookmarkCharacter").value("naero"));
		assertThat(countDeco(b)).isZero();
	}

	private void insertSavedDeco(long id, String frame, String sashStyle, String sashColorShort) {
		jdbcTemplate.update("""
			INSERT INTO member_profile_deco (member_id, portrait, frame, background, sash_style, sash_color_short,
			  sash_color_long, sash_icon_left, sash_icon_right, bookmark_character, bookmark_face, bookmark_face_style, bookmark_color)
			VALUES (?, '/images/characters/standing/naero.webp', ?, 'cream', ?, ?, 'wine', 'none', 'none',
			  'naero', 'happy', 'generated', 'bronze')
			""", id, frame, sashStyle, sashColorShort);
	}

	@Test
	@DisplayName("저장해 둔 값이 잠겨도(표 변경) 다른 칸 저장은 통과하고, 새로 바꾼 잠긴 칸만 409 (BE-98)")
	void savedLockedValueDoesNotBlock() throws Exception {
		long id = registerMember();
		insertSavedDeco(id, "gold", "none", "wine");	// 표가 바뀌어 잠긴 테두리를 저장해 둔 상황
		Map<String, String> body = defaults();
		body.put("frame", "gold");
		body.put("bookmarkColor", "navy");

		putDeco(id, body).andExpect(status().isOk());

		body.put("frame", "navy");	// 저장값과 다른 잠긴 테두리
		putDeco(id, body)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.details[0].field").value("frame"));
	}

	@Test
	@DisplayName("안 쓰이던 칸에 넣어 둔 잠긴 값은 쓰이게 바꿀 때 409 (BE-98)")
	void inactiveSavedValueRechecked() throws Exception {
		long id = registerMember();
		insertSavedDeco(id, "basic", "none", "gold");	// 책갈피 없음 + 짧은 책갈피 색 gold(잠김)
		Map<String, String> body = defaults();
		body.put("sashStyle", "short");
		body.put("sashColorShort", "gold");

		putDeco(id, body)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.details[0].field").value("sashColorShort"));
	}
}
