package com.team2.project.common.exception;

import java.sql.SQLException;

/**
 * DB 유니크 제약 위반 판별 (ApiExceptionAdvice와 ExperienceTransitionService가 함께 쓴다).
 * 동시 요청으로 같은 행을 두 번 저장하려 할 때 공통으로 이 판별을 쓴다.
 */
public final class UniqueViolations {

	/** PostgreSQL 유니크 위반 SQLSTATE */
	private static final String UNIQUE_VIOLATION = "23505";

	private UniqueViolations() {
	}

	public static boolean isUniqueViolation(Throwable e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sqlException && UNIQUE_VIOLATION.equals(sqlException.getSQLState())) {
				return true;
			}
		}
		return false;
	}
}
