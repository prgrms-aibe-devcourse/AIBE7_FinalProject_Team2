package com.team2.project.experience.domain;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import lombok.Getter;

/**
 * 현재 체험 상태에서 할 수 없는 요청 → INVALID_STATE (409, currentStatus 포함)
 * 화면은 currentStatus에 맞는 화면으로 이동한다 (API 명세 1-6)
 */
@Getter
public class InvalidExperienceStateException extends BusinessException {

	private final ExperienceStatus experienceStatus;	// 응답의 currentStatus와 같은 값 (코드에서 쓰기 쉽도록 열거형으로도 보관)

	public InvalidExperienceStateException(ExperienceStatus currentStatus, String message) {
		super(ErrorCode.INVALID_STATE, message, currentStatus.name(), null);
		this.experienceStatus = currentStatus;
	}
}
