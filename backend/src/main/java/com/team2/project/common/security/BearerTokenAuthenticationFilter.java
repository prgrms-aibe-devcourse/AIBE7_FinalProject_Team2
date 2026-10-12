package com.team2.project.common.security;

import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.security.token.AccessTokenVerifier;
import com.team2.project.common.security.token.MemberPrincipal;
import com.team2.project.common.security.token.TokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 사용자 체인의 Bearer 토큰 확인 (확장 단계, BE-57 · WBS 7.4.1)
 *
 * - Authorization 헤더가 없거나 Bearer가 아닌 스킴(Basic 등)이면 아무것도 하지 않는다 → 익명(익명 ID 쿠키)으로 지금처럼 동작.
 *   다른 스킴은 앞단(Nginx Basic 인증 · 모니터링 scrape 등)이 붙인 것일 수 있고, 사용자 체인에는 그 스킴의 인증 방식이 없다(PR #106 리뷰)
 * - Bearer 헤더는 반드시 검증한다. 토큰이 비었거나, 검증기(AccessTokenVerifier 빈)가 아직 없거나,
 *   검증기가 TokenException을 던지면 401(TOKEN_INVALID · TOKEN_EXPIRED)로 끝낸다. 잘못된 토큰을 익명으로 바꾸지 않는다
 * - 검증 성공이면 MemberPrincipal로 인증해 SecurityContext에 넣는다(이 요청 동안만, 세션에 저장하지 않음)
 * - Authorization 헤더가 여러 개이거나(스킴과 관계없이), Bearer 토큰에 RFC 6750 문자(영숫자 - . _ ~ + / 와 끝의 =) 밖의 글자(공백 · 쉼표 등)가 있으면
 *   검증기를 부르지 않고 401 TOKEN_INVALID (프록시가 헤더를 합친 값 · 첫 값만 보는 우회를 막는다)
 * - 인프라 오류(TokenException이 아닌 예외)는 토큰 오류 401로 바꾸지 않는다 → 500 INTERNAL_ERROR(공통 에러 형식)
 *
 * 이 필터는 ExceptionTranslationFilter보다 앞에서 돌아 exceptionHandling 설정을 타지 않으므로 오류 응답을 직접 쓴다.
 * 스프링 빈으로 등록하지 않는다(서블릿 필터로 자동 등록되면 관리자 체인 · 모든 요청에도 걸린다). SecurityConfig가 사용자 체인에만 넣는다.
 * 토큰 값은 로그에 남기지 않는다.
 */
@Slf4j
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_SCHEME = "Bearer";
	private static final String BEARER = BEARER_SCHEME + " ";
	/** RFC 6750 b64token: 영숫자 - . _ ~ + / 뒤에 = 패딩 */
	private static final Pattern TOKEN_CHARS = Pattern.compile("[A-Za-z0-9\\-._~+/]+=*");

	private final ObjectProvider<AccessTokenVerifier> verifiers;
	private final JsonSecurityErrorHandler errors;
	private final SecurityContextRepository contextRepository;
	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	public BearerTokenAuthenticationFilter(ObjectProvider<AccessTokenVerifier> verifiers, JsonSecurityErrorHandler errors,
		SecurityContextRepository contextRepository) {
		this.verifiers = verifiers;
		this.errors = errors;
		this.contextRepository = contextRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		List<String> headers = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
		if (headers.isEmpty()) {
			chain.doFilter(request, response);
			return;
		}
		if (headers.size() > 1) {
			reject(request, response, ErrorCode.TOKEN_INVALID, "Authorization 헤더가 여러 개");
			return;
		}
		String header = headers.get(0);
		if (!isBearerScheme(header)) {	// Basic 등 다른 스킴은 이 필터가 다루지 않는다 → 익명
			chain.doFilter(request, response);
			return;
		}
		String token = bearerToken(header);
		if (token == null) {
			reject(request, response, ErrorCode.TOKEN_INVALID, "Bearer 토큰 형식 오류");
			return;
		}
		AccessTokenVerifier verifier = verifiers.getIfAvailable();
		if (verifier == null) {
			reject(request, response, ErrorCode.TOKEN_INVALID, "토큰 검증기가 아직 없음(WBS 7.4.3)");
			return;
		}
		MemberPrincipal member;
		try {
			member = verifier.verify(token);
		} catch (TokenException e) {
			reject(request, response, e.reason().errorCode(), e.reason().name());
			return;
		} catch (RuntimeException e) {	// 인프라 오류(Redis 등). 토큰 오류로 바꾸지 않고 500, 원인은 서버 로그에만
			serverError(request, response, e);
			return;
		}
		if (member == null) {	// 검증기 구현 오류. 인증 없이 통과시키지 않는다
			serverError(request, response, new IllegalStateException("AccessTokenVerifier가 null을 돌려줬다"));
			return;
		}
		SecurityContext context = contextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(member, null, member.authorities()));
		contextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
		chain.doFilter(request, response);
	}

	/** 스킴이 Bearer인가(대소문자 무시). "Bearer" 한 단어 또는 "Bearer " 뒤에 무엇이든 */
	private static boolean isBearerScheme(String header) {
		return header.regionMatches(true, 0, BEARER_SCHEME, 0, BEARER_SCHEME.length())
			&& (header.length() == BEARER_SCHEME.length() || header.charAt(BEARER_SCHEME.length()) == ' ');
	}

	/** "Bearer {토큰}"의 토큰. 비었거나 RFC 6750 문자 밖이면 null */
	private static String bearerToken(String header) {
		if (header.length() <= BEARER.length() || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
			return null;
		}
		String token = header.substring(BEARER.length()).trim();
		return TOKEN_CHARS.matcher(token).matches() ? token : null;
	}

	private void reject(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode, String reason)
		throws IOException {
		contextHolder.clearContext();
		log.info("사용자 토큰 거절: {} {} ({})", request.getMethod(), request.getRequestURI(), reason);
		errors.write(response, errorCode);
	}

	private void serverError(HttpServletRequest request, HttpServletResponse response, RuntimeException e)
		throws IOException {
		contextHolder.clearContext();
		log.error("사용자 토큰 검증 중 서버 오류: {} {}", request.getMethod(), request.getRequestURI(), e);
		errors.write(response, ErrorCode.INTERNAL_ERROR);
	}
}
