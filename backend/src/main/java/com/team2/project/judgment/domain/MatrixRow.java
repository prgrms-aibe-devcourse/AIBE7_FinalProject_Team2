package com.team2.project.judgment.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.team2.project.legalcase.domain.RevealStage;

/**
 * 세 판결 비교 매트릭스 한 행 (API 14 matrix)
 * 고려하지 않은 주체는 direction이 null이다(ERD 결정 #1의 "—" 표시).
 * summaryTag는 응답에 넣지 않는다(API 명세 matrix 예시에 없다) — RuleSentences가 규칙 문장을 만들 때만 쓴다(BE-26 확정).
 */
public record MatrixRow(
	Long factorId,
	String label,
	RevealStage revealStage,
	Direction user,
	Direction ai,
	Direction court,
	MatrixCategory category,
	@JsonIgnore String summaryTag
) { }
