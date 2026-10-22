package com.team2.project.legalcase.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * 신문 지면 콘텐츠 (BE-92, COMMON-41). 사건마다 1건. PR #138 script.json의 paper 필드 중
 * headline · deck · caption · article만 담는다 — photo는 {@link LegalCase#getThumbnailUrl()},
 * extraPhotos는 {@link CaseIncidentPhoto}를 그대로 재사용한다(새 테이블에 중복 저장하지 않는다).
 */
@Entity
@Table(name = "case_paper")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CasePaper {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id")
	private LegalCase legalCase;

	private String desk;

	private String headline;

	private String deck;

	private String caption;

	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> article;

	@CreationTimestamp
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

	public static CasePaper of(LegalCase legalCase, String desk, String headline, String deck, String caption,
			List<String> article) {
		CasePaper paper = new CasePaper();
		paper.legalCase = legalCase;
		paper.desk = desk;
		paper.headline = headline;
		paper.deck = deck;
		paper.caption = caption;
		paper.article = List.copyOf(article);
		return paper;
	}

	/** 다시 생성(덮어쓰기). 사건마다 신문 지면은 1건이라 새로 만들지 않고 내용만 바꾼다 */
	public void overwrite(String desk, String headline, String deck, String caption, List<String> article) {
		this.desk = desk;
		this.headline = headline;
		this.deck = deck;
		this.caption = caption;
		this.article = List.copyOf(article);
	}

	public void changeHeadline(String headline) {
		this.headline = headline;
	}

	public void changeDeck(String deck) {
		this.deck = deck;
	}

	public void changeCaption(String caption) {
		this.caption = caption;
	}

	public void changeArticle(List<String> article) {
		this.article = List.copyOf(article);
	}
}
