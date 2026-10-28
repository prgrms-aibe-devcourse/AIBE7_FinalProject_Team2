package com.team2.project.admin.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A-13-14 공개 취소 사유 (1 ~ 200자). 감사 로그에 남긴다 */
public record AdminUnpublishRequest(@NotBlank @Size(max = 200) String reason) {

	/** 앞뒤 공백은 잘라서 받는다. 검증 · 길이 · 저장 모두 자른 값 기준 (A-10과 같음) */
	public AdminUnpublishRequest {
		reason = reason == null ? null : reason.strip();
	}
}
