package com.team2.project.experience.domain;

import lombok.Getter;

/**
 * 섹션 확인 순서를 건너뛴 요청 (API 명세 STEP_OUT_OF_ORDER)
 * 에러 응답 변환은 공통 예외 처리(BE-4)에서 한다.
 */
@Getter
public class ReviewStepOutOfOrderException extends RuntimeException {

	private final int expectedStep;

	private final int requestedStep;

	public ReviewStepOutOfOrderException(int expectedStep, int requestedStep) {
		super("섹션 확인 순서를 건너뛸 수 없습니다. 다음 섹션: " + expectedStep + ", 요청: " + requestedStep);
		this.expectedStep = expectedStep;
		this.requestedStep = requestedStep;
	}
}
