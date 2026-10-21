package com.team2.project.auth.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import tools.jackson.databind.json.JsonMapper;

/**
 * OAuth 왕복의 인가 요청(state 등)을 세션 없이 보관한다 (확장 단계, BE-62 · WBS 7.4.2, #106 "D 결정")
 *
 * - 인가 요청 내용은 Redis 토큰 DB에 JSON으로 두고(TTL 5분), 브라우저에는 state 값만 짧은 HttpOnly 쿠키로 준다.
 *   요청 객체를 쿠키에 직렬화해 넣지 않는다 — 사용자가 고칠 수 있는 값을 서버가 역직렬화하지 않기 위해서다
 * - 콜백의 state 파라미터와 쿠키 값이 같아야만 꺼낸다(로그인 CSRF 방지: 남이 시작한 로그인을 내 브라우저에서 끝내지 못함)
 * - 꺼낼 때는 Redis GETDEL로 한 번만 쓴다(같은 콜백 재사용 방지)
 * - 쿠키는 SameSite=Lax여야 한다: 카카오에서 돌아오는 콜백은 다른 사이트에서 온 최상위 이동이라 Strict면 쿠키가 빠진다
 * 빈으로 등록하지 않는다(KakaoOAuthConfig가 만든다)
 */
public class OAuthAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

	static final String COOKIE = "NLNB_OAUTH_STATE";
	private static final String KEY_PREFIX = "oauth2:auth-request:";
	private static final Duration TTL = Duration.ofMinutes(5);

	private final StringRedisTemplate redis;
	private final JsonMapper jsonMapper;
	private final String cookiePath;

	public OAuthAuthorizationRequestRepository(StringRedisTemplate tokenRedisTemplate, JsonMapper jsonMapper,
		String cookiePath) {
		this.redis = tokenRedisTemplate;
		this.jsonMapper = jsonMapper;
		this.cookiePath = cookiePath;
	}

	@Override
	public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
		return matchedState(request).map(state -> redis.opsForValue().get(key(state))).map(this::fromJson).orElse(null);
	}

	@Override
	public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request,
		HttpServletResponse response) {
		if (authorizationRequest == null) {
			clearCookie(response);
			return;
		}
		String state = authorizationRequest.getState();
		redis.opsForValue().set(key(state), toJson(authorizationRequest), TTL);
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(state, TTL).toString());
	}

	@Override
	public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
		HttpServletResponse response) {
		OAuth2AuthorizationRequest found = matchedState(request)
			.map(state -> redis.opsForValue().getAndDelete(key(state)))
			.map(this::fromJson)
			.orElse(null);
		clearCookie(response);
		return found;
	}

	/** 콜백의 state 파라미터가 쿠키 값과 같을 때만 그 값을 돌려준다(시간 차 비교를 피하려 MessageDigest.isEqual) */
	private static Optional<String> matchedState(HttpServletRequest request) {
		String param = request.getParameter(OAuth2ParameterNames.STATE);
		if (param == null || param.isBlank()) {
			return Optional.empty();
		}
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
			.filter(cookie -> COOKIE.equals(cookie.getName()))
			.map(Cookie::getValue)
			.filter(value -> MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8),
				param.getBytes(StandardCharsets.UTF_8)))
			.findFirst();
	}

	private void clearCookie(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
	}

	private ResponseCookie cookie(String value, Duration maxAge) {
		return ResponseCookie.from(COOKIE, value)
			.httpOnly(true)
			.secure(true)
			.sameSite("Lax")
			.path(cookiePath)
			.maxAge(maxAge)
			.build();
	}

	private static String key(String state) {
		return KEY_PREFIX + state;
	}

	private String toJson(OAuth2AuthorizationRequest r) {
		return jsonMapper.writeValueAsString(new Stored(r.getAuthorizationUri(), r.getClientId(), r.getRedirectUri(),
			r.getScopes(), r.getState(), r.getAdditionalParameters(), r.getAttributes(), r.getAuthorizationRequestUri()));
	}

	private OAuth2AuthorizationRequest fromJson(String json) {
		Stored s = jsonMapper.readValue(json, Stored.class);
		return OAuth2AuthorizationRequest.authorizationCode()
			.authorizationUri(s.authorizationUri())
			.clientId(s.clientId())
			.redirectUri(s.redirectUri())
			.scopes(s.scopes())
			.state(s.state())
			.additionalParameters(s.additionalParameters())
			.attributes(s.attributes())
			.authorizationRequestUri(s.authorizationRequestUri())
			.build();
	}

	/** Redis에 두는 인가 요청 내용(JSON). RedisConfig 규칙대로 JDK 직렬화는 쓰지 않는다 */
	record Stored(String authorizationUri, String clientId, String redirectUri, Set<String> scopes, String state,
		Map<String, Object> additionalParameters, Map<String, Object> attributes, String authorizationRequestUri) {
	}
}
