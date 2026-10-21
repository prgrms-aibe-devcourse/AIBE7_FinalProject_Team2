package com.team2.project.legalcase.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 사건 발단 사진 (BE-91, COMMON-41). 등장인물은 얼굴 없는 회색 엑스트라, 내로 · 남불은 미등장.
 * 사건 대표사진(신문용)은 별도 테이블 없이 {@link LegalCase#getThumbnailUrl()}을 그대로 쓴다.
 *
 * 사진은 AI가 직접 만들지 않고 관리자가 업로드한다(결정: docs/decisions/BE-91.md 재검토). {@link #prompt}는
 * 관리자가 사진을 만들 때 참고한 추천 프롬프트를 남겨 두는 선택 항목이라 비어 있을 수 있다.
 */
@Entity
@Table(name = "case_incident_photo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseIncidentPhoto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id")
	private LegalCase legalCase;

	private String imageUrl;

	private String s3Key;

	private String caption;

	/** 관리자가 사진을 만들 때 참고한 추천 프롬프트(선택, 없을 수 있다) */
	private String prompt;

	private int displayOrder;

	@CreationTimestamp
	private Instant createdAt;

	public static CaseIncidentPhoto of(LegalCase legalCase, String imageUrl, String s3Key, String caption,
			String prompt, int displayOrder) {
		CaseIncidentPhoto photo = new CaseIncidentPhoto();
		photo.legalCase = legalCase;
		photo.imageUrl = imageUrl;
		photo.s3Key = s3Key;
		photo.caption = caption;
		photo.prompt = prompt;
		photo.displayOrder = displayOrder;
		return photo;
	}
}
