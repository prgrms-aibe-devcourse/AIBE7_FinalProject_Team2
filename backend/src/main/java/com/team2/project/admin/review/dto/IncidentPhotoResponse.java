package com.team2.project.admin.review.dto;

import com.team2.project.legalcase.domain.CaseIncidentPhoto;
import java.time.Instant;

/** 사건 발단 사진 (BE-91). s3Key는 내부 전용이라 내려주지 않는다 */
public record IncidentPhotoResponse(Long id, String imageUrl, String caption, int displayOrder, Instant createdAt) {

	public static IncidentPhotoResponse from(CaseIncidentPhoto photo) {
		return new IncidentPhotoResponse(photo.getId(), photo.getImageUrl(), photo.getCaption(),
			photo.getDisplayOrder(), photo.getCreatedAt());
	}
}
