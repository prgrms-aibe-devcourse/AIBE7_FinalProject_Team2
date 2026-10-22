package com.team2.project.legalcase.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 사건 발단 · 결론 사진 (BE-91, COMMON-41 / 역할 칸 BE-78 2차 검토 · BE-101). 등장인물은 얼굴 없는
 * 회색 엑스트라, 내로 · 남불은 미등장. 사건 대표사진(신문용)은 별도 테이블 없이
 * {@link LegalCase#getThumbnailUrl()}을 그대로 쓴다.
 *
 * 사진은 AI가 직접 만들지 않고 관리자가 업로드한다(결정: docs/decisions/BE-91.md 재검토). {@link #prompt}는
 * 관리자가 사진을 만들 때 참고한 추천 프롬프트를 남겨 두는 선택 항목이라 비어 있을 수 있다.
 *
 * 테이블 · API 경로 이름(case_incident_photo, /photos/incident)은 "발단(incident)"이지만, BE-78 2차
 * 검토 이후 발단 · 결론 사진을 모두 담는다(이름은 바꾸지 않음 — docs/decisions/BE-78.md 참고).
 *
 * {@link #role}은 1단계 마이그레이션(V26)에서 추가됐다. 이 결정 이전에 올라온 사진(사건마다 3번째
 * 이상, {@code display_order} 2 이상)은 역할이 없어 {@code null}일 수 있다 — 그런 행은 지면 ·
 * 공개 응답에서 제외하고 관리자 목록에만 노출한다.
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

	/** 1단계 동안 role과 함께 유지하는 옛 순번 칸(ORIGIN = 0, CONCLUSION = 1). 2 · 3단계에서 뺀다 */
	private int displayOrder;

	@Enumerated(EnumType.STRING)
	private IncidentPhotoRole role;

	@CreationTimestamp
	private Instant createdAt;

	public static CaseIncidentPhoto of(LegalCase legalCase, String imageUrl, String s3Key, String caption,
			String prompt, IncidentPhotoRole role) {
		CaseIncidentPhoto photo = new CaseIncidentPhoto();
		photo.legalCase = legalCase;
		photo.imageUrl = imageUrl;
		photo.s3Key = s3Key;
		photo.caption = caption;
		photo.prompt = prompt;
		photo.role = role;
		photo.displayOrder = role.legacyDisplayOrder();
		return photo;
	}

	/**
	 * 같은 역할로 다시 올렸을 때 기존 행을 바꿔 치운다(교체). id는 그대로 유지한다. 이전 S3 객체는
	 * 지우지 않는다 — 업로드 키에 시각이 붙어 매번 새 키가 되므로 캐시로 옛 사진이 다시 보이지는
	 * 않는다(결정: docs/decisions/BE-78.md, BE-91의 고아 객체 트레이드오프와 같은 기준).
	 */
	public void replace(String imageUrl, String s3Key, String caption, String prompt) {
		this.imageUrl = imageUrl;
		this.s3Key = s3Key;
		this.caption = caption;
		this.prompt = prompt;
	}
}
