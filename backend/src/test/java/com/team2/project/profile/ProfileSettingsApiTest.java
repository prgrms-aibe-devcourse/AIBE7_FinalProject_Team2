package com.team2.project.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.support.ApiIntegrationTest;
import com.team2.project.support.FakeMemberTokens;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 프로필 선택 설정 부분 저장 PATCH /profile-settings · 마이페이지 profileSettings (P-8 · P-5 v0.52, BE-104) */
@Import(FakeMemberTokens.class)
class ProfileSettingsApiTest extends ApiIntegrationTest {

	private static final String SETTINGS = "/api/v1/profile-settings";
	private static final String MY_PAGE = "/api/v1/my-page";

	private ResultActions patchSettings(Long memberId, String json) throws Exception {
		var request = patch(SETTINGS).contentType(MediaType.APPLICATION_JSON).content(json);
		return mockMvc.perform(memberId == null ? request : request.header("Authorization", bearer(memberId)));
	}

	private int countSettings(long memberId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM member_profile_setting WHERE member_id = ?",
			Integer.class, memberId);
	}

	@Test
	@DisplayName("P-5: 저장한 적이 없으면 profileSettings는 기본값(최근 · 최근 · 소개 없음), 다른 회원이 저장해도 영향이 없다")
	void myPage_defaults() throws Exception {
		mockMvc.perform(get(MY_PAGE).header("Authorization", bearer(registerMember())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profileSettings.shownCard").value("current"))
			.andExpect(jsonPath("$.profileSettings.profileCardType").value("current"))
			.andExpect(jsonPath("$.profileSettings.bio").value(""))
			.andExpect(jsonPath("$.profileSettings.cardLine").value("type"))
			.andExpect(jsonPath("$.profileSettings.cardLevel").value(false));
		long other = registerMember();
		long saver = registerMember();
		patchSettings(saver, "{\"bio\":\"남의 소개\"}").andExpect(status().isOk());
		mockMvc.perform(get(MY_PAGE).header("Authorization", bearer(other)))
			.andExpect(jsonPath("$.profileSettings.shownCard").value("current"))
			.andExpect(jsonPath("$.profileSettings.bio").value(""));
	}

	@Test
	@DisplayName("P-8: 보낸 칸만 바꾸고 응답은 세 값 전체. 마이페이지에도 그대로 보인다")
	void patch_oneField_returnsAll() throws Exception {
		long me = registerMember();
		patchSettings(me, "{\"shownCard\":\"initial\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shownCard").value("initial"))
			.andExpect(jsonPath("$.profileCardType").value("current"))
			.andExpect(jsonPath("$.bio").value(""));

		mockMvc.perform(get(MY_PAGE).header("Authorization", bearer(me)))
			.andExpect(jsonPath("$.profileSettings.shownCard").value("initial"))
			.andExpect(jsonPath("$.profileSettings.profileCardType").value("current"));
	}

	@Test
	@DisplayName("P-8: 다른 화면에서 다른 칸을 저장해도 앞서 저장한 칸을 덮어쓰지 않는다(부분 저장)")
	void patch_otherField_keepsEarlier() throws Exception {
		long me = registerMember();
		patchSettings(me, "{\"bio\":\"판결은 신중하게\"}").andExpect(status().isOk());
		patchSettings(me, "{\"shownCard\":\"retake\"}").andExpect(status().isOk());	// 재심이 없어도 받는다
		patchSettings(me, "{\"profileCardType\":\"initial\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shownCard").value("retake"))
			.andExpect(jsonPath("$.profileCardType").value("initial"))
			.andExpect(jsonPath("$.bio").value("판결은 신중하게"));
		assertThat(countSettings(me)).isEqualTo(1);
	}

	@Test
	@DisplayName("P-8: 소개는 앞뒤 공백을 빼고 40자까지, 빈 문자열은 소개 지우기")
	void patch_bio_trimmedAndCleared() throws Exception {
		long me = registerMember();
		String forty = "가".repeat(40);
		patchSettings(me, "{\"bio\":\"  " + forty + "  \"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bio").value(forty));
		patchSettings(me, "{\"bio\":\"\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bio").value(""));
		// 이모지(서로게이트 쌍)도 한 글자로 센다 — DB varchar(40)과 같은 기준
		patchSettings(me, "{\"bio\":\"" + "😀".repeat(40) + "\"}").andExpect(status().isOk());
	}

	@Test
	@DisplayName("P-8: cardLine · cardLevel을 저장하고 다른 칸은 그대로 둔다. 마이페이지에도 보인다")
	void patch_cardLine_cardLevel() throws Exception {
		long me = registerMember();
		patchSettings(me, "{\"bio\":\"판결은 신중하게\"}").andExpect(status().isOk());
		patchSettings(me, "{\"cardLine\":\"job\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cardLine").value("job"))
			.andExpect(jsonPath("$.cardLevel").value(false))
			.andExpect(jsonPath("$.bio").value("판결은 신중하게"));
		patchSettings(me, "{\"cardLevel\":true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cardLine").value("job"))
			.andExpect(jsonPath("$.cardLevel").value(true));

		mockMvc.perform(get(MY_PAGE).header("Authorization", bearer(me)))
			.andExpect(jsonPath("$.profileSettings.cardLine").value("job"))
			.andExpect(jsonPath("$.profileSettings.cardLevel").value(true));
		assertThat(countSettings(me)).isEqualTo(1);
	}

	@Test
	@DisplayName("P-8: 형식이 틀리면 400 VALIDATION_ERROR이고 아무것도 저장하지 않는다")
	void patch_invalid_rejected() throws Exception {
		long me = registerMember();
		List<String> bodies = List.of(
			"{}",										// 바꿀 칸이 없음
			"[]",										// 객체가 아님
			"{\"shownCard\":\"first\"}",				// 모르는 칸 값
			"{\"profileCardType\":null}",				// null은 "보내지 않음"과 다르다
			"{\"shownCard\":1}",						// 문자열이 아님
			"{\"bio\":null}",
			"{\"cardLine\":\"level\"}",				// 모르는 값
			"{\"cardLine\":null}",
			"{\"cardLine\":1}",
			"{\"cardLevel\":\"true\"}",				// boolean이 아님
			"{\"cardLevel\":1}",
			"{\"cardLevel\":null}",
			"{\"bio\":\"" + "가".repeat(41) + "\"}",	// 41자
			"{\"bio\":\"첫 줄\\n둘째 줄\"}",			// 줄바꿈
			"{\"bio\":\"a\\u0000b\"}",				// NUL(DB에 저장할 수 없어 500이 나던 값)
			"{\"bio\":\"a\\tb\"}",					// 탭 등 제어 문자
			"{\"bio\":\"한 줄\",\"isPublic\":true}");	// 모르는 칸(공개 범위는 두지 않는다)
		for (String body : bodies) {
			patchSettings(me, body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		patchSettings(me, "{\"bio\":\"한 줄\",\"nickname\":\"x\"}")
			.andExpect(jsonPath("$.details[0].field").value("nickname"));
		assertThat(countSettings(me)).isZero();
	}

	@Test
	@DisplayName("P-8 · P-5: 비회원은 저장도 마이페이지도 401 UNAUTHORIZED이고 아무것도 만들지 않는다 (쿠키는 무시)")
	void guest_unauthorized() throws Exception {
		int before = jdbcTemplate.queryForObject("SELECT count(*) FROM member_profile_setting", Integer.class);
		patchSettings(null, "{\"profileCardType\":\"retake\"}")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(patch(SETTINGS).contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"x\"}")
				.cookie(new jakarta.servlet.http.Cookie(COOKIE, java.util.UUID.randomUUID().toString())))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get(MY_PAGE))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member_profile_setting", Integer.class)).isEqualTo(before);
	}

	@Test
	@DisplayName("P-8: 다른 회원의 설정은 내 저장에 섞이지 않는다")
	void otherMemberIsolated() throws Exception {
		long a = registerMember();
		long b = registerMember();
		patchSettings(a, "{\"bio\":\"A의 소개\"}").andExpect(status().isOk());
		patchSettings(b, "{\"shownCard\":\"initial\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bio").value(""));
		mockMvc.perform(get(MY_PAGE).header("Authorization", bearer(a)))
			.andExpect(jsonPath("$.profileSettings.bio").value("A의 소개"))
			.andExpect(jsonPath("$.profileSettings.shownCard").value("current"));
	}

	@Test
	@DisplayName("P-8 · P-7: 프로필 선택 설정은 꾸미기 저장과 따로 있어 서로 덮어쓰지 않는다")
	void patch_doesNotTouchProfileDeco() throws Exception {
		long me = registerMember();
		patchSettings(me, "{\"shownCard\":\"initial\"}").andExpect(status().isOk());
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member_profile_deco WHERE member_id = ?",
			Integer.class, me)).isZero();
		mockMvc.perform(get(MY_PAGE).header("Authorization", bearer(me)))
			.andExpect(jsonPath("$.profileDeco.frame").value("basic"))	// 꾸미기는 기본값 그대로
			.andExpect(jsonPath("$.profileSettings.shownCard").value("initial"));
	}
}
