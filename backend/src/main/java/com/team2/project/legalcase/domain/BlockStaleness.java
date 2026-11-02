package com.team2.project.legalcase.domain;

import java.util.List;
import java.util.Set;

/**
 * 대본 블록 하나의 낡음 (BE-126 2차). 블록을 만들 때 본 값({@link CaseScriptBlock#getBasis()})과 지금 값을 비교한 결과다.
 *
 * <p><b>낡음은 표시가 기본이다</b>(해송님 결정 2026-11-01 — 승인을 막지 않고, 관리자가 "확인했음"을 누르면 근거를 지금 값으로
 * 맞춘다). 다만 {@link #BLOCKING} 값이 바뀐 낡음은 승인을 막는다 — 사건 내용 · 기사가 바뀐 블록은 옛 내용을 말하고(1차부터 막았다),
 * 민감 플래그가 바뀐 블록은 안내 · 표현 가드 없이 만들어졌을 수 있다.
 *
 * @param reasons 바뀐 근거 키 (예: {@code U0} · {@code cast} · {@code paper} · {@code mood}). 비어 있으면 낡지 않았다
 * @param unknown 근거에 없어 비교할 수 없는 키 — 이관 · 증거조사만 만든 대본. "모름 = 낡지 않음 + 표시"(11/01 합의)
 */
public record BlockStaleness(List<String> reasons, List<String> unknown) {

	/** 승인을 막는 근거 키 */
	public static final Set<String> BLOCKING = Set.of(ScriptUnit.CASE_BASIS, ScriptUnit.PAPER_BASIS, "sensitiveFlags");

	public boolean stale() {
		return !reasons.isEmpty();
	}

	/** 승인을 막는 낡음인지 (재생성하거나 "확인했음"으로 푼다) */
	public boolean blocksApproval() {
		return reasons.stream().anyMatch(BLOCKING::contains);
	}
}
