package com.team2.project.common.security.token;

import com.team2.project.common.exception.ErrorCode;
import java.util.Objects;

/**
 * 액세스 토큰이 잘못됐거나 만료됨 (확장 단계, BE-57)
 *
 * {@link AccessTokenVerifier}는 토큰 자체의 문제일 때만 이 예외를 던진다. 사용자 체인의 Bearer 필터가 받아
 * 401({@code TOKEN_INVALID} · {@code TOKEN_EXPIRED})로 응답한다.
 * Redis 장애 같은 인프라 오류는 이 예외로 감싸지 않는다(그대로 던지면 500이 된다. 토큰 오류로 보이게 하지 않는다).
 * 메시지에 토큰 값을 넣지 않는다.
 */
public class TokenException extends RuntimeException {

	public enum Reason {
		/** 형식 · 서명 · 발급자 · 폐기(블랙리스트) 등 받아들일 수 없는 토큰 */
		INVALID(ErrorCode.TOKEN_INVALID),
		/** 서명은 맞지만 유효 기간이 지남. 화면은 재발급을 시도한다 */
		EXPIRED(ErrorCode.TOKEN_EXPIRED);

		private final ErrorCode errorCode;

		Reason(ErrorCode errorCode) {
			this.errorCode = errorCode;
		}

		public ErrorCode errorCode() {
			return errorCode;
		}
	}

	private final Reason reason;

	public TokenException(Reason reason, String message) {
		super(message);
		this.reason = Objects.requireNonNull(reason, "reason");
	}

	public TokenException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = Objects.requireNonNull(reason, "reason");
	}

	public static TokenException invalid(String message) {
		return new TokenException(Reason.INVALID, message);
	}

	public static TokenException expired(String message) {
		return new TokenException(Reason.EXPIRED, message);
	}

	public Reason reason() {
		return reason;
	}
}
