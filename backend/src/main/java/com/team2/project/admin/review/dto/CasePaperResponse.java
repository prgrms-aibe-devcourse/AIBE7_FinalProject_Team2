package com.team2.project.admin.review.dto;

import com.team2.project.legalcase.domain.CasePaper;
import java.time.Instant;
import java.util.List;

/**
 * 신문 지면 콘텐츠 (BE-92). {@code photo}는 {@code legal_case.thumbnail_url}, {@code extraPhotos}는
 * {@link IncidentPhotoResponse}(BE-91 발단 사진)를 그대로 재사용한다 — 같은 사진을 중복 저장하지 않는다.
 */
public record CasePaperResponse(String desk, String headline, String deck, String photo, String caption,
		List<String> article, List<IncidentPhotoResponse> extraPhotos, Instant updatedAt) {

	public static CasePaperResponse of(CasePaper paper, String photo, List<IncidentPhotoResponse> extraPhotos) {
		return new CasePaperResponse(paper.getDesk(), paper.getHeadline(), paper.getDeck(), photo, paper.getCaption(),
			paper.getArticle(), extraPhotos, paper.getUpdatedAt());
	}
}
