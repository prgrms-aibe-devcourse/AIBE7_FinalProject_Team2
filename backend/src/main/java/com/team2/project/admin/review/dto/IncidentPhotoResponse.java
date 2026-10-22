package com.team2.project.admin.review.dto;

import com.team2.project.legalcase.domain.CaseIncidentPhoto;
import java.time.Instant;

/**
 * 사건 발단 · 결론 사진 (BE-91, 역할 칸 BE-78 2차 검토 · BE-101). s3Key는 내부 전용이라 내려주지 않는다.
 * {@code role}은 1단계 이전에 올라온 사진(사건마다 3번째 이상)이면 {@code null}일 수 있다 — 정리 대상을
 * 드러내기 위해 관리자 목록(A-13-5)에는 그대로 노출한다.
 */
public record IncidentPhotoResponse(Long id, String imageUrl, String caption, String role, int displayOrder,
		Instant createdAt) {

	public static IncidentPhotoResponse from(CaseIncidentPhoto photo) {
		return new IncidentPhotoResponse(photo.getId(), photo.getImageUrl(), photo.getCaption(),
			photo.getRole() == null ? null : photo.getRole().name(), photo.getDisplayOrder(), photo.getCreatedAt());
	}
}
