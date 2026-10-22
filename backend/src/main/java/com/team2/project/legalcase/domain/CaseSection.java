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
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사건 정보 섹션 (S-04)
 * 섹션 ① 개요는 legal_case.overview를 쓰고 여기에 저장하지 않는다.
 */
@Entity
@Table(name = "case_section")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseSection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id")
	private LegalCase legalCase;

	@Enumerated(EnumType.STRING)
	private SectionStage stage;

	private String sectionType;		// FACTS, DAMAGE, LAW_TERM 등 (값이 늘어날 수 있어 문자열)

	private String title;

	private String content;

	/**
	 * 섹션마다 원소 형식이 다른 배열 (ERD 3-1 case_section)
	 * - DAMAGE: 객체 {label, value} / LAW_TERM: 객체 {term, desc} → Map
	 * - SUMMARY: 문자열 → String
	 * 배열이 아니면 조회가 실패하므로, 시드는 반드시 배열로 넣는다.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	private List<Object> data;

	private int displayOrder;

	/** 적재(BE-93 2차). displayOrder는 사건 입력의 sections 배열 순서(1부터)다 */
	public static CaseSection of(LegalCase legalCase, SectionStage stage, String sectionType, String title,
			String content, List<Object> data, int displayOrder) {
		CaseSection entity = new CaseSection();
		entity.legalCase = legalCase;
		entity.stage = stage;
		entity.sectionType = sectionType;
		entity.title = title;
		entity.content = content;
		entity.data = data;
		entity.displayOrder = displayOrder;
		return entity;
	}
}
