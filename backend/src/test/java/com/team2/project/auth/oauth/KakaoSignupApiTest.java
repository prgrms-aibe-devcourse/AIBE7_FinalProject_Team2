package com.team2.project.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.common.redis.RedisConfig;
import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.common.web.RefreshTokenCookies;
import com.team2.project.member.domain.TermsVersion;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 카카오 첫 가입의 약관 동의 M-12(가입) · M-13(취소) (BE-145 · docs/decisions/BE-145.md)
 *
 * 실제 PostgreSQL · Redis와 보안 체인(Origin 검증)을 거쳐 확인한다. 카카오 서버는 부르지 않는다 —
 * 가입 대기는 {@link KakaoSignupPending#begin}으로 직접 만든다. 테스트가 만든 회원 · 익명 기록 · Redis 키만 지운다
 */
@TestPropertySource(properties = {
	"kakao.oauth.client-id=test-client-id",
	"kakao.oauth.client-secret=test-client-secret"
})
class KakaoSignupApiTest extends ApiIntegrationTest {

	private static final String SIGNUP = KakaoSignupPending.PATH;
	private static final String SAME_ORIGIN = "http://localhost";
	private static final String OTHER_ORIGIN = "https://evil.example.com";
	private static final String KAKAO_ID = "7700005678";
	private static final String PENDING_KEY_PREFIX = "kakao-signup:pending:";

	@Autowired
	@Qualifier(RedisConfig.TOKEN)
	private StringRedisTemplate tokenRedis;

	@Autowired
	private KakaoSignupPending signupPending;

	private final List<String> redisKeys = new ArrayList<>();
	private final List<UUID> anonymousIds = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		redisKeys.forEach(tokenRedis::delete);
		anonymousIds.forEach(id -> jdbcTemplate.update("DELETE FROM anonymous_user WHERE id = ?", id));
		jdbcTemplate.update("""
			DELETE FROM anonymous_user WHERE member_id IN
			    (SELECT id FROM member WHERE provider = 'KAKAO' AND provider_user_id = ?)
			""", KAKAO_ID);
		jdbcTemplate.update("DELETE FROM member WHERE provider = 'KAKAO' AND provider_user_id = ?", KAKAO_ID);
	}

	/** 카카오 콜백이 하는 일: 가입 대기를 만들고 그 쿠키를 돌려준다 */
	private Cookie newPending() {
		MockHttpServletResponse response = new MockHttpServletResponse();
		signupPending.begin(response, KAKAO_ID);
		Cookie cookie = response.getCookie(KakaoSignupPending.COOKIE);
		assertThat(cookie).isNotNull();
		redisKeys.add(PENDING_KEY_PREFIX + cookie.getValue());
		return cookie;
	}

	private boolean pendingExists(Cookie cookie) {
		return Boolean.TRUE.equals(tokenRedis.hasKey(PENDING_KEY_PREFIX + cookie.getValue()));
	}

	private int memberCount() {
		return jdbcTemplate.queryForObject(
			"SELECT count(*) FROM member WHERE provider = 'KAKAO' AND provider_user_id = ?", Integer.class, KAKAO_ID);
	}

	private static MockHttpServletRequestBuilder agree(String origin, Cookie pending, String body) {
		MockHttpServletRequestBuilder request = post(SIGNUP).contentType(MediaType.APPLICATION_JSON).content(body);
		if (origin != null) {
			request.header(HttpHeaders.ORIGIN, origin);
		}
		if (pending != null) {
			request.cookie(pending);
		}
		return request;
	}

	private static String setCookies(MockHttpServletResponse response) {
		return String.join("\n", response.getHeaders(HttpHeaders.SET_COOKIE));
	}

	/** 로그인 쿠키(리프레시 토큰)의 Redis 키를 정리 대상에 올린다 */
	private void trackRefresh(MvcResult result) {
		Cookie refresh = result.getResponse().getCookie(RefreshTokenCookies.NAME);
		assertThat(refresh).as("로그인 쿠키").isNotNull();
		redisKeys.add("refresh:" + refresh.getValue());
	}

	@Test
	@DisplayName("동의하면 회원과 동의 기록이 한 번에 만들어지고 로그인 쿠키가 심기며 가입 대기는 쓰인다")
	void agree_createsMemberWithConsentAndLogsIn() throws Exception {
		Cookie pending = newPending();

		MvcResult result = mockMvc.perform(agree(SAME_ORIGIN, pending, "{\"agreed\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value(startsWith("방청객 ")))
			.andReturn();

		trackRefresh(result);
		MockHttpServletResponse response = result.getResponse();
		assertThat(response.getContentAsString()).as("토큰은 본문에 싣지 않는다").doesNotContain("token");
		// 가입 대기 쿠키는 지운다(Max-Age=0)
		assertThat(setCookies(response)).contains(KakaoSignupPending.COOKIE + "=;").contains("Max-Age=0");
		assertThat(pendingExists(pending)).as("가입 대기는 한 번 쓰면 사라진다").isFalse();
		// 회원 행과 동의 기록이 함께 있다
		var row = jdbcTemplate.queryForMap("""
			SELECT terms_agreed_at, terms_version, nickname FROM member
			WHERE provider = 'KAKAO' AND provider_user_id = ?
			""", KAKAO_ID);
		assertThat(row.get("terms_agreed_at")).isNotNull();
		assertThat(row.get("terms_version")).isEqualTo(TermsVersion.CURRENT);
		assertThat(row.get("nickname")).as("카카오에서 닉네임을 받지 않는다").isNull();
	}

	@Test
	@DisplayName("같은 가입 대기는 한 번만 쓸 수 있고, 두 번째는 409 KAKAO_SIGNUP_EXPIRED다")
	void pending_isSingleUse() throws Exception {
		Cookie pending = newPending();
		trackRefresh(mockMvc.perform(agree(SAME_ORIGIN, pending, "{\"agreed\":true}")).andExpect(status().isOk()).andReturn());

		mockMvc.perform(agree(SAME_ORIGIN, pending, "{\"agreed\":true}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("KAKAO_SIGNUP_EXPIRED"));

		assertThat(memberCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("동의하지 않은 요청(없음 · null · false · 문자열 · 숫자)은 400 agreed이고, 가입 대기는 그대로라 이어서 동의할 수 있다")
	void notAgreed_keepsPending() throws Exception {
		Cookie pending = newPending();

		for (String body : new String[] {"{}", "{\"agreed\":null}", "{\"agreed\":false}", "{\"agreed\":\"true\"}", "{\"agreed\":1}"}) {
			mockMvc.perform(agree(SAME_ORIGIN, pending, body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details[*].field", everyItem(is("agreed"))));
		}

		assertThat(memberCount()).as("회원 행이 없다").isZero();
		assertThat(pendingExists(pending)).as("동의 검사가 먼저라 가입 대기를 쓰지 않는다").isTrue();
		trackRefresh(mockMvc.perform(agree(SAME_ORIGIN, pending, "{\"agreed\":true}")).andExpect(status().isOk()).andReturn());
		assertThat(memberCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("가입 대기 쿠키가 없거나, 형식이 다르거나, Redis에 없으면 409 KAKAO_SIGNUP_EXPIRED이고 회원을 만들지 않는다")
	void missingOrUnknownPending() throws Exception {
		Cookie malformed = new Cookie(KakaoSignupPending.COOKIE, "not-a-token");
		Cookie unknown = new Cookie(KakaoSignupPending.COOKIE, "A".repeat(43));

		for (Cookie cookie : new Cookie[] {null, malformed, unknown}) {
			MvcResult result = mockMvc.perform(agree(SAME_ORIGIN, cookie, "{\"agreed\":true}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("KAKAO_SIGNUP_EXPIRED"))
				.andReturn();
			assertThat(result.getResponse().getCookie(RefreshTokenCookies.NAME)).as("로그인 쿠키를 심지 않는다").isNull();
		}

		assertThat(memberCount()).isZero();
	}

	@Test
	@DisplayName("가입 대기가 TTL로 사라졌으면 409 KAKAO_SIGNUP_EXPIRED다")
	void expiredPending() throws Exception {
		Cookie pending = newPending();
		tokenRedis.delete(PENDING_KEY_PREFIX + pending.getValue());

		mockMvc.perform(agree(SAME_ORIGIN, pending, "{\"agreed\":true}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("KAKAO_SIGNUP_EXPIRED"));

		assertThat(memberCount()).isZero();
	}

	@Test
	@DisplayName("이 브라우저의 익명 기록은 가입하는 회원에 연결된다 (동의 → 가져오기 순서의 바탕)")
	void agree_linksAnonymousRecord() throws Exception {
		UUID anonymousId = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO anonymous_user (id, member_id) VALUES (?, NULL)", anonymousId);
		anonymousIds.add(anonymousId);
		Cookie pending = newPending();

		trackRefresh(mockMvc.perform(agree(SAME_ORIGIN, pending, "{\"agreed\":true}")
				.cookie(new Cookie(AnonymousIdCookie.NAME, anonymousId.toString())))
			.andExpect(status().isOk())
			.andReturn());

		Long memberId = jdbcTemplate.queryForObject(
			"SELECT id FROM member WHERE provider = 'KAKAO' AND provider_user_id = ?", Long.class, KAKAO_ID);
		assertThat(jdbcTemplate.queryForObject("SELECT member_id FROM anonymous_user WHERE id = ?", Long.class, anonymousId))
			.isEqualTo(memberId);
	}

	@Test
	@DisplayName("같은 카카오 계정의 가입 대기가 둘(두 탭)이어도 회원은 하나만 만들어지고, 처음 동의 기록은 바뀌지 않는다")
	void twoPendingsForSameAccount_createOneMember() throws Exception {
		Cookie first = newPending();
		Cookie second = newPending();
		trackRefresh(mockMvc.perform(agree(SAME_ORIGIN, first, "{\"agreed\":true}")).andExpect(status().isOk()).andReturn());
		String firstConsent = jdbcTemplate.queryForObject(
			"SELECT terms_agreed_at::text FROM member WHERE provider = 'KAKAO' AND provider_user_id = ?", String.class, KAKAO_ID);

		trackRefresh(mockMvc.perform(agree(SAME_ORIGIN, second, "{\"agreed\":true}")).andExpect(status().isOk()).andReturn());

		assertThat(memberCount()).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT terms_agreed_at::text FROM member WHERE provider = 'KAKAO' AND provider_user_id = ?", String.class, KAKAO_ID))
			.isEqualTo(firstConsent);
	}

	@Test
	@DisplayName("M-13: 동의하지 않고 닫으면 가입 대기가 지워지고 DB에는 아무것도 남지 않는다")
	void cancel_leavesNothing() throws Exception {
		Cookie pending = newPending();

		MvcResult result = mockMvc.perform(delete(SIGNUP).header(HttpHeaders.ORIGIN, SAME_ORIGIN).cookie(pending))
			.andExpect(status().isNoContent())
			.andReturn();

		assertThat(setCookies(result.getResponse())).contains(KakaoSignupPending.COOKIE + "=;").contains("Max-Age=0");
		assertThat(pendingExists(pending)).isFalse();
		assertThat(memberCount()).isZero();
		mockMvc.perform(agree(SAME_ORIGIN, pending, "{\"agreed\":true}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("KAKAO_SIGNUP_EXPIRED"));
		assertThat(memberCount()).isZero();
	}

	@Test
	@DisplayName("M-13: 가입 대기가 이미 없어도 204다")
	void cancel_isIdempotent() throws Exception {
		mockMvc.perform(delete(SIGNUP).header(HttpHeaders.ORIGIN, SAME_ORIGIN)).andExpect(status().isNoContent());
		mockMvc.perform(delete(SIGNUP).header(HttpHeaders.ORIGIN, SAME_ORIGIN).cookie(new Cookie(KakaoSignupPending.COOKIE, "x")))
			.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("Origin이 없거나 다르면 가입 · 취소 모두 403이고, 가입 대기는 그대로다 (로그인 CSRF)")
	void originIsVerified() throws Exception {
		Cookie pending = newPending();

		for (String origin : new String[] {null, OTHER_ORIGIN}) {
			MockHttpServletRequestBuilder cancel = delete(SIGNUP).cookie(pending);
			if (origin != null) {
				cancel.header(HttpHeaders.ORIGIN, origin);
			}
			for (MockHttpServletRequestBuilder request : List.of(agree(origin, pending, "{\"agreed\":true}"), cancel)) {
				MvcResult result = mockMvc.perform(request)
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("FORBIDDEN"))
					.andReturn();
				assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
			}
		}

		assertThat(pendingExists(pending)).isTrue();
		assertThat(memberCount()).isZero();
	}
}
