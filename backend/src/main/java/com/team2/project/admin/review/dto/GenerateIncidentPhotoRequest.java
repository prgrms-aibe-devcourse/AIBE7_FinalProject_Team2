package com.team2.project.admin.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 사건 발단 사진 생성 요청 (BE-91). incidentDescription은 영문 프롬프트 재료, caption은 화면에 보일 설명(선택) */
public record GenerateIncidentPhotoRequest(@NotBlank @Size(max = 1000) String incidentDescription,
		@Size(max = 200) String caption) {

	public GenerateIncidentPhotoRequest {
		incidentDescription = incidentDescription == null ? null : incidentDescription.strip();
		caption = caption == null ? null : caption.strip();
	}
}
