package com.team2.project.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

/** 프로필 선택 설정 부분 저장 PATCH /profile-settings · 마이페이지 profileSettings (P-8 · P-5 v0.52, BE-104) */
class ProfileSettingsApiTest extends ApiIntegrationTest {

	private static final String SETTINGS = "/api/v1/profile-settings";
	private static final String MY_PAGE = "/api/v1/my-page";

	/** 체험이 없는 사용자(부모 정리 대상이 아님)는 여기서 지운다 */
	private final List<UUID> bareUserIds = new ArrayList<>();

	@AfterEach
	void deleteBareUsers() {
		bareUserIds.forEach(id -> {
			jdbcTemplate.update("DELETE FROM attendance WHERE anonymous_user_id = ?", id);	// 발급 시 출석(BE-98)
			jdbcTemplate.update("DELETE FROM profile_setting WHERE anonymous_user_id = ?", id);
			jdbcTemplate.update("DELETE FROM anonymous_user WHERE id = ?", id);
		});
		bareUserIds.clear();
	}

	private Cookie newUser() {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO anonymous_user (id) VALUES (?)", id);
		bareUserIds.add(id);
		return new Cookie(COOKIE, id.toString());
	}

	private ResultActions patchSettings(Cookie cookie, String json) throws Exception {
		var request = patch(SETTINGS).contentType(MediaType.APPLICATION_JSON).content(json);
		return mockMvc.perform(cookie == null ? request : request.cookie(cookie));
	}

	private int countSettings(Cookie cookie) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM profile_setting WHERE anonymous_user_id = ?::uuid",
			Integer.class, cookie.getValue());
	}

	@Test
	@DisplayName("P-5: 저장한 적이 없으면 profileSettings는 기본값(최근 · 최근 · 소개 없음), 쿠키가 없어도 같다")
	void myPage_defaults() throws Exception {
		mockMvc.perform(get(MY_PAGE).cookie(newUser()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profileSettings.shownCard").value("current"))
			.andExpect(jsonPath("$.profileSettings.profileCardType").value("current"))
			.andExpect(jsonPath("$.profileSettings.bio").value(""));
		mockMvc.perform(get(MY_PAGE))
			.andExpect(jsonPath("$.profileSettings.shownCard").value("current"))
			.andExpect(jsonPath("$.profileSettings.bio").value(""));
	}

	@Test
	@DisplayName("P-8: 보낸 칸만 바꾸고 응답은 세 값 전체. 마이페이지에도 그대로 보인다")
	void patch_oneField_returnsAll() throws Exception {
		Cookie cookie = newUser();
		patchSettings(cookie, "{\"shownCard\":\"initial\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shownCard").value("initial"))
			.andExpect(jsonPath("$.profileCardType").value("current"))
			.andExpect(jsonPath("$.bio").value(""));

		mockMvc.perform(get(MY_PAGE).cookie(cookie))
			.andExpect(jsonPath("$.profileSettings.shownCard").value("initial"))
			.andExpect(jsonPath("$.profileSettings.profileCardType").value("current"));
	}

	@Test
	@DisplayName("P-8: 다른 화면에서 다른 칸을 저장해도 앞서 저장한 칸을 덮어쓰지 않는다(부분 저장)")
	void patch_otherField_keepsEarlier() throws Exception {
		Cookie cookie = newUser();
		patchSettings(cookie, "{\"bio\":\"판결은 신중하게\"}").andExpect(status().isOk());
		patchSettings(cookie, "{\"shownCard\":\"retake\"}").andExpect(status().isOk());	// 재심이 없어도 받는다
		patchSettings(cookie, "{\"profileCardType\":\"initial\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shownCard").value("retake"))
			.andExpect(jsonPath("$.profileCardType").value("initial"))
			.andExpect(jsonPath("$.bio").value("판결은 신중하게"));
		assertThat(countSettings(cookie)).isEqualTo(1);
	}

	@Test
	@DisplayName("P-8: 소개는 앞뒤 공백을 빼고 40자까지, 빈 문자열은 소개 지우기")
	void patch_bio_trimmedAndCleared() throws Exception {
		Cookie cookie = newUser();
		String forty = "가".repeat(40);
		patchSettings(cookie, "{\"bio\":\"  " + forty + "  \"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bio").value(forty));
		patchSettings(cookie, "{\"bio\":\"\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bio").value(""));
		// 이모지(서로게이트 쌍)도 한 글자로 센다 — DB varchar(40)과 같은 기준
		patchSettings(cookie, "{\"bio\":\"" + "😀".repeat(40) + "\"}").andExpect(status().isOk());
	}

	@Test
	@DisplayName("P-8: 형식이 틀리면 400 VALIDATION_ERROR이고 아무것도 저장하지 않는다")
	void patch_invalid_rejected() throws Exception {
		Cookie cookie = newUser();
		List<String> bodies = List.of(
			"{}",										// 바꿀 칸이 없음
			"[]",										// 객체가 아님
			"{\"shownCard\":\"first\"}",				// 모르는 칸 값
			"{\"profileCardType\":null}",				// null은 "보내지 않음"과 다르다
			"{\"shownCard\":1}",						// 문자열이 아님
			"{\"bio\":null}",
			"{\"bio\":\"" + "가".repeat(41) + "\"}",	// 41자
			"{\"bio\":\"첫 줄\\n둘째 줄\"}",			// 줄바꿈
			"{\"bio\":\"a\\u0000b\"}",				// NUL(DB에 저장할 수 없어 500이 나던 값)
			"{\"bio\":\"a\\tb\"}",					// 탭 등 제어 문자
			"{\"bio\":\"한 줄\",\"isPublic\":true}");	// 모르는 칸(공개 범위는 두지 않는다)
		for (String body : bodies) {
			patchSettings(cookie, body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		patchSettings(cookie, "{\"bio\":\"한 줄\",\"nickname\":\"x\"}")
			.andExpect(jsonPath("$.details[0].field").value("nickname"));
		assertThat(countSettings(cookie)).isZero();
	}

	@Test
	@DisplayName("P-8: 쿠키가 없으면 익명 ID를 발급하고 저장한다. 형식이 틀리면 발급하지 않는다")
	void patch_withoutCookie_issues() throws Exception {
		int usersBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM anonymous_user", Integer.class);
		MockHttpServletResponse rejected = patchSettings(null, "{\"shownCard\":\"first\"}")
			.andExpect(status().isBadRequest())
			.andReturn().getResponse();
		assertThat(rejected.getHeader(HttpHeaders.SET_COOKIE)).isNull();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM anonymous_user", Integer.class)).isEqualTo(usersBefore);

		MockHttpServletResponse saved = patchSettings(null, "{\"profileCardType\":\"retake\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profileCardType").value("retake"))
			.andReturn().getResponse();
		String setCookie = saved.getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith(COOKIE + "=");
		UUID issued = UUID.fromString(setCookie.split(";")[0].substring((COOKIE + "=").length()));
		bareUserIds.add(issued);
		assertThat(countSettings(new Cookie(COOKIE, issued.toString()))).isEqualTo(1);
	}

	@Test
	@DisplayName("P-8 · P-7: 프로필 선택 설정은 꾸미기 저장과 따로 있어 서로 덮어쓰지 않는다")
	void patch_doesNotTouchProfileDeco() throws Exception {
		Cookie cookie = newUser();
		patchSettings(cookie, "{\"shownCard\":\"initial\"}").andExpect(status().isOk());
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM profile_deco WHERE anonymous_user_id = ?::uuid",
			Integer.class, cookie.getValue())).isZero();
		mockMvc.perform(get(MY_PAGE).cookie(cookie))
			.andExpect(jsonPath("$.profileDeco.frame").value("basic"))	// 꾸미기는 기본값 그대로
			.andExpect(jsonPath("$.profileSettings.shownCard").value("initial"));
	}
}
