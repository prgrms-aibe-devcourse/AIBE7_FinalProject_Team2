package com.team2.project.admin.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 사건 대표사진(신문용) 생성 요청 (BE-91). caseSummary는 영문 프롬프트 재료(비식별화한 사건 요약) */
public record GenerateRepresentativePhotoRequest(@NotBlank @Size(max = 1000) String caseSummary) {

	public GenerateRepresentativePhotoRequest {
		caseSummary = caseSummary == null ? null : caseSummary.strip();
	}
}
