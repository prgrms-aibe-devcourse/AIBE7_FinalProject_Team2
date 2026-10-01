package com.team2.project.legalcase.domain;

/**
 * 판단 요소를 사용자가 처음 알게 되는 단계 (factor.reveal_stage)
 * DB CHECK 제약과 같은 값을 쓴다.
 */
public enum RevealStage {
	OVERVIEW,	// ① 사건 개요 (사전 판단 선택지 대상)
	DETAIL,
	ARGUMENT,
	LAW
}
