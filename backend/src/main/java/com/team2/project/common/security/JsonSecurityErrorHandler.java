package com.team2.project.common.security;

import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보안 필터에서 막힌 요청을 공통 에러 응답(API 명세 1-4)으로 바꾼다.
 * 컨트롤러 밖(필터)에서 일어나므로 ApiExceptionAdvice가 처리하지 못한다.
 * - 로그인 안 함 → 401 UNAUTHORIZED
 * - 권한 없음 · CSRF 토큰 없음/불일치 → 403 FORBIDDEN (원인은 로그에만)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final JsonMapper jsonMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
		throws IOException {
		write(response, ErrorCode.UNAUTHORIZED);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
		throws IOException {
		log.info("관리자 API 접근 거부: {} {} ({})", request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName());
		write(response, ErrorCode.FORBIDDEN);
	}

	private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(jsonMapper.writeValueAsString(ErrorResponse.of(errorCode)));
	}
}
