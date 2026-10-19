package com.team2.project.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

	private final TraceIdFilter filter = new TraceIdFilter();

	@Test
	void doFilter_noIncomingHeader_generatesNewTraceId() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> seenDuringRequest = new AtomicReference<>();
		FilterChain chain = (req, res) -> seenDuringRequest.set(MDC.get(TraceIdFilter.MDC_KEY));

		filter.doFilter(request, response, chain);

		assertThat(seenDuringRequest.get()).isNotBlank();
		assertThat(response.getHeader(TraceIdFilter.RESPONSE_TRACE_HEADER)).isEqualTo(seenDuringRequest.get());
	}

	@Test
	void doFilter_incomingRequestIdHeader_reusesIt() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(TraceIdFilter.REQUEST_ID_HEADER, "upstream-trace-id");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> seenDuringRequest = new AtomicReference<>();
		FilterChain chain = (req, res) -> seenDuringRequest.set(MDC.get(TraceIdFilter.MDC_KEY));

		filter.doFilter(request, response, chain);

		assertThat(seenDuringRequest.get()).isEqualTo("upstream-trace-id");
		assertThat(response.getHeader(TraceIdFilter.RESPONSE_TRACE_HEADER)).isEqualTo("upstream-trace-id");
	}

	@Test
	void doFilter_invalidIncomingHeader_generatesNewTraceIdInstead() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		// 제어문자(개행)로 로그 줄을 조작하려는 시도. 형식이 안 맞으니 그대로 쓰지 않는다
		request.addHeader(TraceIdFilter.REQUEST_ID_HEADER, "evil\nINFO fake log line");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> seenDuringRequest = new AtomicReference<>();
		FilterChain chain = (req, res) -> seenDuringRequest.set(MDC.get(TraceIdFilter.MDC_KEY));

		filter.doFilter(request, response, chain);

		assertThat(seenDuringRequest.get()).doesNotContain("evil").doesNotContain("\n");
	}

	@Test
	void doFilter_errorDispatch_reusesTraceIdAlreadySetOnResponse() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setDispatcherType(DispatcherType.ERROR);
		MockHttpServletResponse response = new MockHttpServletResponse();
		// 1차 통과(REQUEST 디스패치)에서 이미 응답 헤더를 써 둔 상황을 흉내 낸다
		response.setHeader(TraceIdFilter.RESPONSE_TRACE_HEADER, "original-trace-id");
		AtomicReference<String> seenDuringRequest = new AtomicReference<>();
		FilterChain chain = (req, res) -> seenDuringRequest.set(MDC.get(TraceIdFilter.MDC_KEY));

		filter.doFilter(request, response, chain);

		assertThat(seenDuringRequest.get()).isEqualTo("original-trace-id");
	}

	@Test
	void doFilter_afterRequest_clearsMdcForNextRequestOnSameThread() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = (req, res) -> { };

		filter.doFilter(request, response, chain);

		assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
	}

	@Test
	void doFilter_chainThrows_stillClearsMdc() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = (req, res) -> {
			throw new RuntimeException("boom");
		};

		try {
			filter.doFilter(request, response, chain);
		} catch (Exception ignored) {
			// 이 테스트는 예외 처리가 아니라 MDC 정리를 확인한다
		}

		assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
	}
}
