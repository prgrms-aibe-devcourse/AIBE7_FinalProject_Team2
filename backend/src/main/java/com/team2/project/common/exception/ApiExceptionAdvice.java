package com.team2.project.common.exception;

import java.sql.SQLException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 글로벌 예외 처리. 예외 → 공통 에러 응답(API 명세 1-4) 변환은 여기 한 곳에서만 한다.
 * 응답에는 사용자에게 필요한 메시지만 담고, 원인 · 스택 트레이스는 로그에만 남긴다 (CODE_CONVENTIONS 3-3).
 *
 * ResponseEntityExceptionHandler를 상속해서 Spring MVC가 던지는 요청 처리 예외(폼 바인딩, 형식 · 헤더 ·
 * 경로변수 오류, 지원하지 않는 메서드 · Content-Type, 없는 주소 등 그 목록 전체)를 handleExceptionInternal
 * 한 곳에서 받는다. 개별 예외를 하나씩 @ExceptionHandler로 나열하지 않아도 되고, 목록에서 빠뜨려 500으로
 * 새는 경우도 없앤다 (리뷰 반영).
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionAdvice extends ResponseEntityExceptionHandler {

	/** PostgreSQL 유니크 위반 SQLSTATE */
	private static final String UNIQUE_VIOLATION = "23505";

	/** 비즈니스 예외 (도메인 예외 포함) → ErrorCode 그대로 */
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
		return ResponseEntity.status(e.getErrorCode().getStatus()).body(ErrorResponse.from(e));
	}

	/**
	 * 경로 · 쿼리 파라미터 타입 오류(예: {caseId}에 숫자가 아닌 값).
	 * ResponseEntityExceptionHandler가 이 예외를 자체 처리하지 않을 수 있어 안전하게 직접 잡는다.
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		log.debug("경로 · 파라미터 타입 오류: {}", e.getMessage());
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

	/** 그 밖의 모든 예외 → INTERNAL_ERROR (원인은 로그에만) */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		return internalError(e);
	}

	/**
	 * ResponseEntityExceptionHandler가 처리하는 모든 Spring MVC 예외의 공통 통로.
	 * BindException(@Valid 포함)은 필드별 사유를 details에 담는다. 4xx는 VALIDATION_ERROR,
	 * 5xx는 INTERNAL_ERROR로 통일한다 (API 명세에 이 예외들에 대응하는 별도 코드가 없음, 1-5).
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
		HttpStatusCode statusCode, WebRequest request) {
		if (statusCode.is5xxServerError()) {
			log.error("처리하지 못한 요청 예외", ex);
			return ResponseEntity.status(statusCode).body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
		}
		List<FieldErrorDetail> details = ex instanceof BindException bindException
			? bindException.getFieldErrors().stream()
				.map(error -> new FieldErrorDetail(error.getField(), error.getCode()))
				.toList()
			: null;
		log.debug("요청 처리 중 오류: {}", ex.getMessage());
		return ResponseEntity.status(statusCode).body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, details));
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
