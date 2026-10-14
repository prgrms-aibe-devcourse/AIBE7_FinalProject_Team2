package com.team2.project.admin.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A-10 반려 사유 (1 ~ 500자, judgment.review_note varchar(500)) */
public record AdminRejectRequest(@NotBlank @Size(max = 500) String reason) {

	/** 앞뒤 공백은 잘라서 받는다. 검증 · 길이 · 저장 모두 자른 값 기준 (#132 리뷰) */
	public AdminRejectRequest {
		reason = reason == null ? null : reason.strip();
	}
}
