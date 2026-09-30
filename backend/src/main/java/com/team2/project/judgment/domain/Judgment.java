package com.team2.project.judgment.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 판단 (사용자 사전 판단 · 최종 판결, AI, 재판부). 지금은 사전 판단 저장에 필요한 컬럼만 매핑한다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "judgment")
public class Judgment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long caseId;

	@Enumerated(EnumType.STRING)
	private JudgmentSubject subjectType;

	@Enumerated(EnumType.STRING)
	private JudgmentTiming timing;

	private Long experienceId;

	private Long rangeOptionId;

	private boolean isPublished;

	/** 사용자 사전 판단: 형량 구간만 고르고 형벌 값은 비운다. 사용자 판단은 항상 공개다. */
	public static Judgment userPreJudgment(Long caseId, Long experienceId, Long rangeOptionId) {
		Judgment judgment = new Judgment();
		judgment.caseId = caseId;
		judgment.subjectType = JudgmentSubject.USER;
		judgment.timing = JudgmentTiming.PRE;
		judgment.experienceId = experienceId;
		judgment.rangeOptionId = rangeOptionId;
		judgment.isPublished = true;
		return judgment;
	}
}
