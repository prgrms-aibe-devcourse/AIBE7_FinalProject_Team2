package com.team2.project.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
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
 * 형식이 이상하면(길이 · 문자 제한 위반) 외부 클라이언트가 보낸 값을 그대로 로그에 꽂는 셈이라 믿지 않고 새로 만든다 —
 * 제어문자로 로그 줄을 조작하거나, 남의 traceId를 재사용해 추적을 흐리는 것을 막기 위해서다(#129 리뷰).
 *
 * 응답 헤더로도 돌려줘서, 문의가 들어왔을 때 사용자가 본 요청을 로그에서 바로 찾을 수 있게 한다.
 * 컨트롤러에서 처리하지 못한 예외가 컨테이너의 /error로 다시 디스패치될 때도 같은 traceId가 보이도록,
 * 에러 디스패치에서도 다시 실행되게 하고(1차 통과 때 이미 써 둔 응답 헤더 값을 그대로 재사용한다).
 *
 * traceId는 요청마다 값이 다른 고유 식별자다 — Prometheus 메트릭 라벨(7.2.1)이나 Loki 스트림 라벨로는 절대 쓰지 않는다
 * (라벨/스트림마다 시계열이 하나씩 생겨 무한히 늘어난다). 로그 "내용"의 필드 하나로만 남긴다.
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter implements Ordered {

	static final String MDC_KEY = "traceId";
	static final String REQUEST_ID_HEADER = "X-Request-Id";
	static final String RESPONSE_TRACE_HEADER = "X-Trace-Id";

	/** 로그 줄 조작(제어문자) · 과도한 길이를 막는다. 흔히 쓰는 요청 ID 모양(영숫자 · 점 · 밑줄 · 하이픈)만 믿는다 */
	private static final Pattern VALID_TRACE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		// 기본값(true)이면 /error 재디스패치 때는 이 필터를 건너뛰어, 그 사이 로그에 traceId가 빠진다
		return false;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		// 에러 재디스패치라 이미 1차 통과에서 응답 헤더를 써 뒀으면 그 값을 그대로 이어쓴다(같은 요청은 같은 traceId)
		String existing = response.getHeader(RESPONSE_TRACE_HEADER);
		String incoming = request.getHeader(REQUEST_ID_HEADER);
		String traceId;
		if (StringUtils.hasText(existing)) {
			traceId = existing;
		} else if (StringUtils.hasText(incoming) && VALID_TRACE_ID.matcher(incoming).matches()) {
			traceId = incoming;
		} else {
			traceId = UUID.randomUUID().toString();
		}
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
