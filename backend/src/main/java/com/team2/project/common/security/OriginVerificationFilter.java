package com.team2.project.common.security;

import com.team2.project.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 쿠키 기반 요청의 CSRF 대책: Origin 검증 (확장 단계, BE-94 · WBS 7.4.3, C 결정)
 *
 * 리프레시 토큰 쿠키(NLNB_RT)는 브라우저가 자동으로 실어 보내므로, 악성 사이트가 몰래 같은 요청을 흉내 낼
 * 수 있다(CSRF). 재발급 · 로그아웃처럼 이 쿠키로만 인증하는 상태 변경 요청에 한해 Origin(없으면 Referer)
 * 이 우리 서버 자신을 가리키는지 확인한다. 별도 CSRF 토큰 발급 · 검증 왕복을 두지 않는 가벼운 대책이다
 * (docs/tech-stack.md 3장 "리프레시 토큰 쿠키").
 *
 * 포트는 비교하지 않는다(Nginx가 TLS를 끝내고 내부 포트로 넘기므로 외부에서 본 포트와 다를 수 있다).
 * 스프링 빈으로 등록하지 않는다(사용자 체인에만 넣는다, BearerTokenAuthenticationFilter와 같은 이유).
 *
 * 보호 경로 판정은 원시 {@code getRequestURI()} 문자열 비교가 아니라 {@link RequestMatcher}로 한다 —
 * {@code getRequestURI()}는 context path를 포함하고 디코딩되지 않아, context path가 있는 배포나
 * percent-encoding된 경로(예: {@code /token/%72eissue})에서는 Spring MVC가 디코딩해 컨트롤러까지
 * 통과시키는 요청을 이 필터가 놓칠 수 있다(PR #218 리뷰)
 */
@Slf4j
public class OriginVerificationFilter extends OncePerRequestFilter {

	private final RequestMatcher protectedRequests;
	private final JsonSecurityErrorHandler errors;

	public OriginVerificationFilter(RequestMatcher protectedRequests, JsonSecurityErrorHandler errors) {
		this.protectedRequests = protectedRequests;
		this.errors = errors;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		if (!protectedRequests.matches(request)) {
			chain.doFilter(request, response);
			return;
		}
		String origin = originOf(request);
		if (origin == null || !matchesServer(origin, request)) {
			log.info("Origin 검증 실패: {} {} (Origin={})", request.getMethod(), request.getRequestURI(), origin);
			errors.write(response, ErrorCode.FORBIDDEN);
			return;
		}
		chain.doFilter(request, response);
	}

	/** Origin 헤더, 없으면 Referer에서 scheme://host만 뽑는다. 둘 다 없으면 null(브라우저 요청이면 거의 항상 있다) */
	private static String originOf(HttpServletRequest request) {
		String origin = request.getHeader(HttpHeaders.ORIGIN);
		if (origin != null && !origin.isBlank()) {
			return origin;
		}
		String referer = request.getHeader(HttpHeaders.REFERER);
		if (referer == null || referer.isBlank()) {
			return null;
		}
		try {
			URI uri = new URI(referer);
			if (uri.getScheme() == null || uri.getHost() == null) {
				return null;
			}
			return uri.getScheme() + "://" + uri.getHost();
		} catch (URISyntaxException e) {
			return null;
		}
	}

	/**
	 * {@code request.getScheme()} · {@code getServerName()}은 Nginx가 TLS를 끝내고 HTTP로 넘기는 배포에서
	 * {@code server.forward-headers-strategy: native}(Tomcat RemoteIpValve)가 켜져 있어야 원래 scheme · Host를
	 * 돌려준다. 그 설정과 Nginx의 forwarded 헤더 전달은 BE-62(카카오 로그인, `forward-headers-strategy: native`
	 * 도입 예정)에서 함께 들어간다 — docs/decisions/BE-94.md "추후 재검토" 참고. 그 전까지 운영에 먼저 올라가면
	 * Origin(https)과 서버가 본 scheme(http)이 달라 재발급 · 로그아웃이 403으로 막힐 수 있다
	 */
	private static boolean matchesServer(String origin, HttpServletRequest request) {
		try {
			URI uri = new URI(origin);
			return request.getScheme().equalsIgnoreCase(uri.getScheme())
				&& request.getServerName().equalsIgnoreCase(uri.getHost());
		} catch (URISyntaxException e) {
			return false;
		}
	}
}
