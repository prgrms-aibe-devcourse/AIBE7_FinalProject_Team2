package com.team2.project.experience.domain;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import lombok.Getter;

/**
 * 섹션 확인 순서를 건너뛴 요청 → STEP_OUT_OF_ORDER (409)
 */
@Getter
public class ReviewStepOutOfOrderException extends BusinessException {

	private final int expectedStep;

	private final int requestedStep;

	public ReviewStepOutOfOrderException(int expectedStep, int requestedStep) {
		super(ErrorCode.STEP_OUT_OF_ORDER,
			"섹션 확인 순서를 건너뛸 수 없습니다. 다음 섹션: " + expectedStep + ", 요청: " + requestedStep);
		this.expectedStep = expectedStep;
		this.requestedStep = requestedStep;
	}
}
