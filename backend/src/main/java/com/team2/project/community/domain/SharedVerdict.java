package com.team2.project.community.domain;

/**
 * 판결 공유 글에 붙인 내 판결 (BE-115). 붙인 시점의 값을 글에 그대로 남긴다(jsonb).
 * 판결한 사람 자신의 판결만 담는다 — AI 판결 · 실제 판결은 넣지 않는다(앵커링 방지, FE-47)
 *
 * @param upCount   형량을 높이는 쪽으로 고른 판단 요소 수
 * @param downCount 형량을 낮추는 쪽으로 고른 판단 요소 수
 */
public record SharedVerdict(String penaltyType, String reducedTo, Integer prisonMonths, Long fineAmount,
	Integer suspensionMonths, int upCount, int downCount) {
}
