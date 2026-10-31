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
import java.util.Objects;
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

	/**
	 * 기사의 판 (BE-126). 1막 해설(U1) 블록이 어느 판의 기사를 보고 만들었는지 이 값으로 남긴다. 내용이 바뀔 때만 오른다
	 * ({@code updatedAt}을 쓰지 않는 이유는 {@code LegalCase.contentRevision}과 같다)
	 */
	private int revision = 1;

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
		this.revision++;		// 다시 생성한 기사는 새 판이다
		this.desk = desk;
		this.headline = headline;
		this.deck = deck;
		this.caption = caption;
		this.article = List.copyOf(article);
	}

	public void changeHeadline(String headline) {
		revise(this.headline, headline);
		this.headline = headline;
	}

	public void changeDeck(String deck) {
		revise(this.deck, deck);
		this.deck = deck;
	}

	public void changeCaption(String caption) {
		revise(this.caption, caption);
		this.caption = caption;
	}

	public void changeArticle(List<String> article) {
		revise(this.article, article);
		this.article = List.copyOf(article);
	}

	/** 값이 실제로 바뀔 때만 판을 올린다 — 같은 값으로 저장해도 U1이 낡았다고 보이지 않게 */
	private void revise(Object before, Object after) {
		if (!Objects.equals(before, after)) {
			this.revision++;
		}
	}
}
