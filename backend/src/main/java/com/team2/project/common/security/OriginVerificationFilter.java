package com.team2.project.common.security;

import com.team2.project.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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
 */
@Slf4j
public class OriginVerificationFilter extends OncePerRequestFilter {

	private final Set<String> protectedPaths;
	private final JsonSecurityErrorHandler errors;

	public OriginVerificationFilter(Set<String> protectedPaths, JsonSecurityErrorHandler errors) {
		this.protectedPaths = Set.copyOf(protectedPaths);
		this.errors = errors;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		if (!protectedPaths.contains(request.getRequestURI())) {
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
