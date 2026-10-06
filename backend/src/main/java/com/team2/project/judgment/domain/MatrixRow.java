package com.team2.project.judgment.domain;

import com.team2.project.legalcase.domain.RevealStage;

/**
 * 세 판결 비교 매트릭스 한 행 (API 14 matrix)
 * 고려하지 않은 주체는 direction이 null이다(ERD 결정 #1의 "—" 표시).
 */
public record MatrixRow(
	Long factorId,
	String label,
	RevealStage revealStage,
	Direction user,
	Direction ai,
	Direction court,
	MatrixCategory category
) { }
