package com.team2.project.profile.domain;

import java.util.Set;

/**
 * 프로필 선택 설정 값 (API 명세 P-8 · P-5 profileSettings v0.52, BE-104 · cardLine · cardLevel은 v0.65, BE-114). 응답과 저장값이 같은 모양이다.
 * shownCard: 공개 성향카드, profileCardType: 프로필카드 아래 성향 — 둘 다 initial(초심) · retake(재심) · current(최근) 중 하나,
 * 서로 별개의 값이다. bio: 한 줄 소개(빈 문자열 = 소개 없음)
 * cardLine: 프로필카드 아래에 보일 줄 — type(성향) · signature(한 줄 소개) · job(직업) 중 하나,
 * cardLevel: 레벨을 그 줄 윗줄에 붙일지
 */
public record ProfileSettingsValues(String shownCard, String profileCardType, String bio, String cardLine, boolean cardLevel) {

	/** 고를 수 있는 칸 (화면 SHOWN_SLOTS · SLOT_ORDER와 같은 값) */
	public static final Set<String> SLOTS = Set.of("initial", "retake", "current");

	/** 프로필카드 아래 줄로 고를 수 있는 값 */
	public static final Set<String> CARD_LINES = Set.of("type", "signature", "job");

	/** 한 줄 소개 최대 글자 수(앞뒤 공백을 뺀 뒤, 유니코드 코드 포인트 기준. 화면 BIO_MAX와 같다) */
	public static final int BIO_MAX = 40;

	/** 저장한 적이 없으면 최근 · 최근 · 소개 없음 · 성향 줄 · 레벨 없음 (화면 기본값과 같다) */
	public static final ProfileSettingsValues DEFAULTS = new ProfileSettingsValues("current", "current", "", "type", false);
}
