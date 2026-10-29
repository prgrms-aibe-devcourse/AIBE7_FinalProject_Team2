package com.team2.project.legalcase.dto;

import com.team2.project.legalcase.domain.CaseIncidentPhoto;
import com.team2.project.legalcase.domain.CasePaper;
import com.team2.project.legalcase.domain.CasePhotoUrls;
import com.team2.project.legalcase.domain.IncidentPhotoRole;
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

	public static PublicCasePaperResponse of(Long caseId, CasePaper paper, String photo, CaseIncidentPhoto origin,
			CaseIncidentPhoto conclusion) {
		return new PublicCasePaperResponse(paper.getDesk(), paper.getHeadline(), paper.getDeck(),
			CasePhotoUrls.thumbnail(caseId, photo), paper.getCaption(), paper.getArticle(),
			ExtraPhotos.of(caseId, origin, conclusion));
	}

	/**
	 * 발단(origin) · 결론(conclusion) 고정 자리. 그 역할의 사진 행이 없으면 규칙 주소(BE-141, {@link CasePhotoUrls})를
	 * 넣는다 — 캡션은 없다. 자리가 비는 일은 없다
	 */
	public record ExtraPhotos(PhotoSlot origin, PhotoSlot conclusion) {
		static ExtraPhotos of(Long caseId, CaseIncidentPhoto origin, CaseIncidentPhoto conclusion) {
			return new ExtraPhotos(PhotoSlot.from(caseId, IncidentPhotoRole.ORIGIN, origin),
				PhotoSlot.from(caseId, IncidentPhotoRole.CONCLUSION, conclusion));
		}
	}

	public record PhotoSlot(String imageUrl, String caption) {
		static PhotoSlot from(Long caseId, IncidentPhotoRole role, CaseIncidentPhoto photo) {
			// 행이 없거나 주소가 비어 있으면 규칙 주소 — 대표 사진(thumbnail)과 같은 기준이다
			if (photo == null || photo.getImageUrl() == null || photo.getImageUrl().isBlank()) {
				return new PhotoSlot(CasePhotoUrls.incident(caseId, role), photo == null ? null : photo.getCaption());
			}
			return new PhotoSlot(photo.getImageUrl(), photo.getCaption());
		}
	}
}
