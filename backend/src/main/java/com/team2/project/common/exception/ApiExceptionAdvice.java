package com.team2.project.common.exception;

import java.sql.SQLException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 글로벌 예외 처리. 예외 → 공통 에러 응답(API 명세 1-4) 변환은 여기 한 곳에서만 한다.
 * 응답에는 사용자에게 필요한 메시지만 담고, 원인 · 스택 트레이스는 로그에만 남긴다 (CODE_CONVENTIONS 3-3).
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionAdvice {

	/** PostgreSQL 유니크 위반 SQLSTATE */
	private static final String UNIQUE_VIOLATION = "23505";

	/** 비즈니스 예외 (도메인 예외 포함) → ErrorCode 그대로 */
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
		return ResponseEntity.status(e.getErrorCode().getStatus()).body(ErrorResponse.from(e));
	}

	/** @Valid 검증 실패 → VALIDATION_ERROR + 필드별 사유 (사유는 제약 이름, 예: NotNull · Min) */
	@ExceptionHandler(BindException.class)
	public ResponseEntity<ErrorResponse> handleBind(BindException e) {
		List<FieldErrorDetail> details = e.getFieldErrors().stream()
			.map(error -> new FieldErrorDetail(error.getField(), error.getCode()))
			.toList();
		return validationError(details);
	}

	/** 요청 형식 오류: JSON 파싱 · 잘못된 열거값, 경로 · 파라미터 타입 오류, 필수 파라미터 누락, 메서드 파라미터 검증 실패 */
	@ExceptionHandler({
		HttpMessageNotReadableException.class,
		MethodArgumentTypeMismatchException.class,
		MissingServletRequestParameterException.class,
		HandlerMethodValidationException.class
	})
	public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
		log.debug("요청 형식 오류: {}", e.getMessage());
		return validationError(null);
	}

	/**
	 * 유니크 제약 위반 → INVALID_STATE (동시 요청으로 사전 판단 · 판결이 두 번 저장되려는 경우)
	 * 체험 시작(API 2)의 동시 요청은 서비스에서 직접 잡아 기존 체험을 200으로 돌려준다 (API 명세 API 2).
	 * 그 밖의 제약 위반은 데이터 오류라 INTERNAL_ERROR로 응답하고 로그를 남긴다.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
		if (isUniqueViolation(e)) {
			log.info("유니크 제약 위반 (동시 요청): {}", e.getMostSpecificCause().getMessage());
			return ResponseEntity.status(ErrorCode.INVALID_STATE.getStatus()).body(ErrorResponse.of(ErrorCode.INVALID_STATE));
		}
		return internalError(e);
	}

	/**
	 * Spring MVC가 정한 요청 오류(없는 주소, 지원하지 않는 메서드 · Content-Type 등)는 HTTP 상태를 그대로 두고
	 * 4xx는 VALIDATION_ERROR(개발 오류, API 명세 1-5)로 응답한다.
	 */
	@ExceptionHandler({
		NoResourceFoundException.class,
		HttpRequestMethodNotSupportedException.class,
		HttpMediaTypeNotSupportedException.class,
		ErrorResponseException.class
	})
	public ResponseEntity<ErrorResponse> handleSpringWeb(Exception e) {
		var status = ((org.springframework.web.ErrorResponse) e).getStatusCode();
		if (status.is5xxServerError()) {
			return internalError(e);
		}
		return ResponseEntity.status(status).body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR));
	}

	/** 그 밖의 모든 예외 → INTERNAL_ERROR (원인은 로그에만) */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		return internalError(e);
	}

	private ResponseEntity<ErrorResponse> validationError(List<FieldErrorDetail> details) {
		return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
			.body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, details));
	}

	private ResponseEntity<ErrorResponse> internalError(Exception e) {
		log.error("처리하지 못한 예외", e);
		return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus()).body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
	}

	private boolean isUniqueViolation(Throwable e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sqlException && UNIQUE_VIOLATION.equals(sqlException.getSQLState())) {
				return true;
			}
		}
		return false;
	}
}
