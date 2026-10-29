package com.team2.project.auth.oauth;

import com.team2.project.common.redis.RedisConfig;
import com.team2.project.common.security.SecurityHeaders;
import com.team2.project.common.security.token.TokenIssuer;
import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.common.web.RefreshTokenCookies;
import com.team2.project.member.service.MemberLoginService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카카오 로그인 — OAuth 전용 보안 체인 (확장 단계, BE-62 · WBS 7.4.2)
 *
 * 체인 순서(B 김현중 제안 · 합의): 관리자 @Order(1) → OAuth @Order(2) → 사용자 @Order(3)
 * - 이 체인은 {@code /api/v1/auth/oauth2/**}만 맡는다. 브라우저 이동 경로라 실패는 JSON 401이 아니라 리다이렉트다
 *   (사용자 체인의 JsonSecurityErrorHandler와 분리). 사용자 체인의 permitAll · Bearer 401 규칙은 그대로다
 * - 세션을 만들지 않는다: STATELESS + 인가 요청은 Redis · state 쿠키(OAuthAuthorizationRequestRepository),
 *   성공 · 실패 처리도 직접 만든 핸들러(기본 SavedRequestAware는 세션을 쓴다). 관리자 세션 쿠키는 읽지 않는다
 * - 경로는 {@code /api} 아래다: Spring 기본 경로(/oauth2/..., /login/oauth2/code/...)는 운영 Nginx에서 SPA로 빠진다
 *   · 시작: GET /api/v1/auth/oauth2/authorization/kakao
 *   · 콜백: GET /api/v1/auth/oauth2/callback/kakao (카카오 콘솔 Redirect URI에 등록)
 * - kakao.oauth.client-id가 없으면 이 설정 전체가 꺼진다(KakaoOAuthEnabled). 그때 위 경로는 사용자 체인으로 가 404다
 * - {baseUrl}은 요청에서 만든다. 운영은 Nginx 뒤라 server.forward-headers-strategy: native가 있어야 https 도메인이 된다
 */
@Configuration
@Conditional(KakaoOAuthEnabled.class)
@EnableConfigurationProperties(KakaoOAuthProperties.class)
public class KakaoOAuthConfig {

	public static final String BASE_PATH = "/api/v1/auth/oauth2";
	public static final String AUTHORIZATION_BASE_URI = BASE_PATH + "/authorization";
	public static final String CALLBACK_BASE_URI = BASE_PATH + "/callback";
	private static final String REGISTRATION_ID = "kakao";

	@Bean
	public ClientRegistrationRepository clientRegistrationRepository(KakaoOAuthProperties properties) {
		return new InMemoryClientRegistrationRepository(kakao(properties));
	}

	@Bean
	@Order(2)
	public SecurityFilterChain oauthLoginSecurityFilterChain(HttpSecurity http,
		ClientRegistrationRepository clientRegistrations, KakaoOAuthProperties properties,
		@Qualifier(RedisConfig.TOKEN) StringRedisTemplate tokenRedisTemplate, JsonMapper jsonMapper,
		MemberLoginService memberLoginService, TokenIssuer tokenIssuer, RefreshTokenCookies refreshTokenCookies,
		AnonymousIdCookie anonymousIdCookie, Clock clock) throws Exception {
		OAuthAuthorizationRequestRepository authorizationRequests =
			new OAuthAuthorizationRequestRepository(tokenRedisTemplate, jsonMapper, BASE_PATH);
		OAuthLoginFailureHandler failure = new OAuthLoginFailureHandler(properties.failureRedirect());
		http
			.securityMatcher(BASE_PATH + "/**")
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.securityContext(context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
			// 상태 변경 API가 없고(GET 이동만), 로그인 CSRF는 state(쿠키 대조)로 막는다
			.csrf(AbstractHttpConfigurer::disable)
			.headers(SecurityHeaders::apply)	// 다른 체인과 같은 보안 헤더 · HSTS 끔 (COMMON-67)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.oauth2Login(oauth -> oauth
				.clientRegistrationRepository(clientRegistrations)
				.authorizationEndpoint(endpoint -> endpoint
					.baseUri(AUTHORIZATION_BASE_URI)
					.authorizationRequestRepository(authorizationRequests))
				.redirectionEndpoint(endpoint -> endpoint.baseUri(CALLBACK_BASE_URI + "/*"))
				.authorizedClientRepository(new NoStoreAuthorizedClientRepository())
				.successHandler(new OAuthLoginSuccessHandler(memberLoginService, tokenIssuer, refreshTokenCookies,
					anonymousIdCookie, clock, properties.successRedirect(), properties.failureRedirect()))
				.failureHandler(failure))
			// 이 체인에서 인증이 필요한 곳은 없다. 혹시 막히면 로그인 페이지(/login)가 아니라 실패 화면으로 보낸다
			.exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) ->
				response.sendRedirect(properties.failureRedirect())));
		return http.build();
	}

	private static ClientRegistration kakao(KakaoOAuthProperties properties) {
		return ClientRegistration.withRegistrationId(REGISTRATION_ID)
			.clientName("Kakao")
			.clientId(properties.clientId())
			.clientSecret(properties.clientSecret())
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}" + CALLBACK_BASE_URI + "/{registrationId}")
			.authorizationUri("https://kauth.kakao.com/oauth/authorize")
			.tokenUri("https://kauth.kakao.com/oauth/token")
			.userInfoUri("https://kapi.kakao.com/v2/user/me")
			// 동의항목(scope)을 요청하지 않는다: 회원번호(id)만 쓰고 개인정보는 받지 않는다
			.userNameAttributeName("id")
			.build();
	}
}
