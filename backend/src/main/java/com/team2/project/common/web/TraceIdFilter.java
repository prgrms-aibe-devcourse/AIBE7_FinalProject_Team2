package com.team2.project.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 traceId를 만들어 로그에 전파한다 (BE-71, WBS 7.2.3).
 *
 * Spring Security 필터 체인(관리자 · 사용자 두 체인 다)보다 먼저 실행되도록 가장 먼저 자리를 잡는다
 * ({@link Ordered#HIGHEST_PRECEDENCE}) — 그래야 인증 과정에서 남는 로그에도 traceId가 붙는다.
 *
 * 들어온 요청에 X-Request-Id가 있으면 그대로 쓴다(Nginx · 다른 서비스가 이미 발급한 추적 ID를 이어받기 위해).
 * 없으면 새로 만든다. 응답 헤더로도 돌려줘서, 문의가 들어왔을 때 사용자가 본 요청을 로그에서 바로 찾을 수 있게 한다.
 *
 * traceId는 요청마다 값이 다른 고유 식별자다 — Prometheus 메트릭 라벨(7.2.1)이나 Loki 스트림 라벨로는 절대 쓰지 않는다
 * (라벨/스트림마다 시계열이 하나씩 생겨 무한히 늘어난다). 로그 "내용"의 필드 하나로만 남긴다.
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter implements Ordered {

	static final String MDC_KEY = "traceId";
	static final String REQUEST_ID_HEADER = "X-Request-Id";
	static final String RESPONSE_TRACE_HEADER = "X-Trace-Id";

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String incoming = request.getHeader(REQUEST_ID_HEADER);
		String traceId = StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString();
		MDC.put(MDC_KEY, traceId);
		response.setHeader(RESPONSE_TRACE_HEADER, traceId);
		try {
			chain.doFilter(request, response);
		} finally {
			// 스레드 풀에서 재사용되는 스레드라, 다음 요청으로 traceId가 새지 않게 반드시 지운다
			MDC.remove(MDC_KEY);
		}
	}
}
