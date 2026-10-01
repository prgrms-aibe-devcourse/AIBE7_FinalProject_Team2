package com.team2.project.experience.domain;

import lombok.Getter;

/**
 * 섹션 확인 번호가 허용 범위(2 ~ 4) 밖인 요청 (API 명세 VALIDATION_ERROR)
 * 에러 응답 변환은 공통 예외 처리(BE-4)에서 한다.
 */
@Getter
public class InvalidReviewStepException extends RuntimeException {

	private final int requestedStep;

	public InvalidReviewStepException(int requestedStep, int minStep, int maxStep) {
		super("섹션 번호는 " + minStep + " ~ " + maxStep + "만 확인할 수 있습니다. 요청: " + requestedStep);
		this.requestedStep = requestedStep;
	}
}
