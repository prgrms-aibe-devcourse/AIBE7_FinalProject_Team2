package com.team2.project.experience.domain;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import lombok.Getter;

/**
 * 섹션 확인 번호가 허용 범위(2 ~ 4) 밖인 요청 → VALIDATION_ERROR (400)
 */
@Getter
public class InvalidReviewStepException extends BusinessException {

	private final int requestedStep;

	public InvalidReviewStepException(int requestedStep, int minStep, int maxStep) {
		super(ErrorCode.VALIDATION_ERROR,
			"섹션 번호는 " + minStep + " ~ " + maxStep + "만 확인할 수 있습니다. 요청: " + requestedStep);
		this.requestedStep = requestedStep;
	}
}
