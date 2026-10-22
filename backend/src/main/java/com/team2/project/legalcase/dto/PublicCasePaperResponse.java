package com.team2.project.legalcase.dto;

import com.team2.project.legalcase.domain.CaseIncidentPhoto;
import com.team2.project.legalcase.domain.CasePaper;
import java.util.List;

/**
 * 공개 신문 지면 (API 17, BE-102). 관리자용 응답(admin.review.dto.CasePaperResponse)과 달리
 * 화면에 필요한 값만 내려준다 — 사진의 내부 id · 순번 · 생성 시각은 뺀다.
 *
 * 발단 · 결론 사진은 배열이 아니라 {@link ExtraPhotos}로 고정 자리에 담는다. 배열로 내려주면 한쪽을
 * 올리지 않았을 때 다른 쪽이 앞자리로 밀려 역할이 뒤바뀌어 보일 수 있다 — role 칸을 둔 이유(BE-101)와
 * 같은 문제라 공개 API도 같은 원칙을 따른다(결정: docs/decisions/BE-78.md).
 */
public record PublicCasePaperResponse(String desk, String headline, String deck, String photo, String caption,
		List<String> article, ExtraPhotos extraPhotos) {

	public static PublicCasePaperResponse of(CasePaper paper, String photo, CaseIncidentPhoto origin,
			CaseIncidentPhoto conclusion) {
		return new PublicCasePaperResponse(paper.getDesk(), paper.getHeadline(), paper.getDeck(), photo,
			paper.getCaption(), paper.getArticle(), ExtraPhotos.of(origin, conclusion));
	}

	/** 발단(origin) · 결론(conclusion) 고정 자리. 그 역할의 사진이 없으면 그 자리는 null */
	public record ExtraPhotos(PhotoSlot origin, PhotoSlot conclusion) {
		static ExtraPhotos of(CaseIncidentPhoto origin, CaseIncidentPhoto conclusion) {
			return new ExtraPhotos(PhotoSlot.from(origin), PhotoSlot.from(conclusion));
		}
	}

	public record PhotoSlot(String imageUrl, String caption) {
		static PhotoSlot from(CaseIncidentPhoto photo) {
			return photo == null ? null : new PhotoSlot(photo.getImageUrl(), photo.getCaption());
		}
	}
}
