package com.team2.project.admin.review.dto;

import jakarta.validation.constraints.Size;

/** 사건 발단 사진 프롬프트 추천 요청 (BE-91 재설계). hint는 선택 — 특정 장면을 더 짚고 싶을 때만 */
public record IncidentPhotoPromptRequest(@Size(max = 500) String hint) {

	public IncidentPhotoPromptRequest {
		hint = hint == null ? null : hint.strip();
	}
}
