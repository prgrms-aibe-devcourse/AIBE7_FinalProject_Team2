package com.team2.project.experience.domain;

import lombok.Getter;

/**
 * 현재 체험 상태에서 할 수 없는 요청 (API 명세 INVALID_STATE)
 * 에러 응답 변환(409, currentStatus 포함)은 공통 예외 처리(BE-4)에서 한다.
 */
@Getter
public class InvalidExperienceStateException extends RuntimeException {

	private final ExperienceStatus currentStatus;

	public InvalidExperienceStateException(ExperienceStatus currentStatus, String message) {
		super(message);
		this.currentStatus = currentStatus;
	}
}
