package com.team2.project.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.common.redis.RedisConfig;
import com.team2.project.common.security.token.TokenIssuer;
import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.common.web.RefreshTokenCookies;
import com.team2.project.member.service.MemberLoginService;
import com.team2.project.support.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 로그인 OAuth 전용 체인 (BE-62 · WBS 7.4.2). 카카오 서버는 부르지 않는다 —
 * 시작 리다이렉트 · state 쿠키 · 콜백 거절 · 성공 처리(핸들러 직접 호출)를 확인한다
 */
@TestPropertySource(properties = {
	"kakao.oauth.client-id=test-client-id",
	"kakao.oauth.client-secret=test-client-secret"
})
class KakaoOAuthFlowTest extends ApiIntegrationTest {

	private static final String START = "/api/v1/auth/oauth2/authorization/kakao";
	private static final String CALLBACK = "/api/v1/auth/oauth2/callback/kakao";
	private static final String SUCCESS = "/auth/callback";
	private static final String FAILURE = "/auth/callback?error=login_failed";
	private static final String KAKAO_ID = "7700001234";

	@Autowired
	@Qualifier(RedisConfig.TOKEN)
	private StringRedisTemplate tokenRedis;

	@Autowired
	private MemberLoginService memberLoginService;

	@Autowired
	private TokenIssuer tokenIssuer;

	@Autowired
	private RefreshTokenCookies refreshTokenCookies;

	@Autowired
	private AnonymousIdCookie anonymousIdCookie;

	@Autowired
	private Clock clock;

	private final List<String> redisKeys = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		redisKeys.forEach(tokenRedis::delete);
		jdbcTemplate.update("""
			DELETE FROM anonymous_user WHERE member_id IN
			    (SELECT id FROM member WHERE provider = 'KAKAO' AND provider_user_id = ?)
			""", KAKAO_ID);
		jdbcTemplate.update("DELETE FROM member WHERE provider = 'KAKAO' AND provider_user_id = ?", KAKAO_ID);
	}

	private OAuthLoginSuccessHandler handler() {
		return new OAuthLoginSuccessHandler(memberLoginService, tokenIssuer, refreshTokenCookies, anonymousIdCookie,
			clock, SUCCESS, FAILURE);
	}

	private static OAuth2AuthenticationToken kakaoLogin(Object kakaoId) {
		Set<SimpleGrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("OAUTH2_USER"));
		DefaultOAuth2User kakaoUser = new DefaultOAuth2User(authorities, Map.of("id", kakaoId), "id");
		return new OAuth2AuthenticationToken(kakaoUser, authorities, "kakao");
	}

	private static String setCookies(MockHttpServletResponse response) {
		return String.join("\n", response.getHeaders(HttpHeaders.SET_COOKIE));
	}

	@Test
	void 시작하면_카카오로_보내고_state를_쿠키와_Redis에만_둔다() throws Exception {
		MvcResult result = mockMvc.perform(get(START)).andExpect(status().is3xxRedirection()).andReturn();

		String location = result.getResponse().getRedirectedUrl();
		assertThat(location).startsWith("https://kauth.kakao.com/oauth/authorize");
		Map<String, List<String>> query = UriComponentsBuilder.fromUriString(location).build(true).getQueryParams();
		assertThat(query.get("client_id")).containsExactly("test-client-id");
		assertThat(URLDecoder.decode(query.get("redirect_uri").get(0), StandardCharsets.UTF_8))
			.endsWith("/api/v1/auth/oauth2/callback/kakao");
		assertThat(query).as("동의항목(scope)을 요청하지 않는다").doesNotContainKey("scope");
		String state = URLDecoder.decode(query.get("state").get(0), StandardCharsets.UTF_8);

		assertThat(setCookies(result.getResponse())).contains(OAuthAuthorizationRequestRepository.COOKIE + "=")
			.contains("HttpOnly").contains("SameSite=Lax").contains("Path=/api/v1/auth/oauth2");
		// 콜백 대조(로그인 CSRF 방지)의 전제: 쿠키 값이 카카오로 보낸 state와 같아야 한다 (PR #229 리뷰)
		Cookie stateCookie = result.getResponse().getCookie(OAuthAuthorizationRequestRepository.COOKIE);
		assertThat(stateCookie).isNotNull();
		assertThat(stateCookie.getValue()).isEqualTo(state);
		assertThat(result.getRequest().getSession(false)).as("세션을 만들지 않는다").isNull();

		String key = "oauth2:auth-request:" + state;
		redisKeys.add(key);
		assertThat(tokenRedis.hasKey(key)).isTrue();
		assertThat(tokenRedis.getExpire(key)).isPositive();
	}

	@Test
	void 카카오_체인도_HSTS를_붙이지_않고_다른_보안_헤더는_붙인다() throws Exception {
		// 카카오 체인은 client-id가 있어야 켜져서(KakaoOAuthEnabled) 이 테스트 클래스에서만 확인할 수 있다.
		// 사용자 · 관리자 체인은 UserSecurityChainTest가 확인한다. HSTS는 Nginx가 붙인다 (COMMON-67, PR #337 리뷰)
		MvcResult result = mockMvc.perform(get(START).secure(true))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().doesNotExist("Strict-Transport-Security"))
			.andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andReturn();
		// 사용자 체인이 아니라 카카오 체인이 응답했는지 (꺼져 있으면 카카오로 보내지 않는다)
		assertThat(result.getResponse().getRedirectedUrl()).startsWith("https://kauth.kakao.com/oauth/authorize");
	}

	@Test
	void state_쿠키가_없거나_다르면_로그인_실패_화면으로_보낸다() throws Exception {
		mockMvc.perform(get(CALLBACK).param("code", "any").param("state", "forged"))
			.andExpect(redirectedUrl(FAILURE));

		mockMvc.perform(get(CALLBACK).param("code", "any").param("state", "forged")
				.cookie(new Cookie(OAuthAuthorizationRequestRepository.COOKIE, "different")))
			.andExpect(redirectedUrl(FAILURE));
	}

	@Test
	void 사용자가_카카오에서_취소하면_실패_화면으로_보낸다() throws Exception {
		mockMvc.perform(get(CALLBACK).param("error", "access_denied").param("state", "whatever"))
			.andExpect(redirectedUrl(FAILURE));
	}

	@Test
	void 성공하면_리프레시_쿠키와_익명_쿠키를_심고_토큰_없이_화면으로_보낸다() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler().onAuthenticationSuccess(new MockHttpServletRequest("GET", CALLBACK), response,
			kakaoLogin(Long.valueOf(KAKAO_ID)));

		assertThat(response.getRedirectedUrl()).isEqualTo(SUCCESS);
		Cookie refresh = response.getCookie(RefreshTokenCookies.NAME);
		assertThat(refresh).isNotNull();
		redisKeys.add("refresh:" + refresh.getValue());
		assertThat(response.getRedirectedUrl()).doesNotContain("token");
		Long memberId = jdbcTemplate.queryForObject(
			"SELECT id FROM member WHERE provider = 'KAKAO' AND provider_user_id = ?", Long.class, KAKAO_ID);
		// 심은 익명 ID 쿠키 값이 실제로 이 회원에 연결된 익명 사용자인지 DB로 확인 (PR #229 리뷰)
		Cookie anonymousId = response.getCookie(AnonymousIdCookie.NAME);
		assertThat(anonymousId).isNotNull();
		assertThat(jdbcTemplate.queryForObject("SELECT member_id FROM anonymous_user WHERE id = ?", Long.class,
			UUID.fromString(anonymousId.getValue()))).isEqualTo(memberId);
	}

	@Test
	void 성공_처리_중_오류가_나면_쿠키를_바꾸지_않고_실패_화면으로_보낸다() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		// 공급자 회원번호 형식이 숫자가 아님 → MemberLoginService가 거절
		handler().onAuthenticationSuccess(new MockHttpServletRequest("GET", CALLBACK), response, kakaoLogin("not-a-number"));

		assertThat(response.getRedirectedUrl()).isEqualTo(FAILURE);
		assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).as("쿠키를 하나도 심지 않는다").isEmpty();
	}
}
