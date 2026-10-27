package com.team2.project.level.domain;

/**
 * 이벤트로 받는 경험치 수급처 (BE-106). 사건 완료 · 성향 테스트 · 출석 · 업적은 기존 테이블에서 계산하므로 여기에 없다 (LevelXp).
 * 같은 (사용자, 수급처, 대상)은 한 번만 준다(xp_event 유니크). 상한 0은 "상한 없음"
 *
 * @param amount     한 번에 주는 XP
 * @param dailyCap   하루(KST) 최대 지급 횟수
 * @param groupCap   같은 묶음(group_key)당 최대 지급 횟수
 */
public enum XpSource {

	POST(30, 3, 0),				// 글 작성
	SHARE_POST(20, 0, 0),		// 판결 공유 글 보너스 (사건당 1회 — 대상 = 사건 ID)
	COMMENT(10, 5, 0),			// 댓글 작성
	LIKE_GIVEN(2, 10, 0),		// 좋아요 누르기 (글당 1회 — 대상 = 글 ID)
	LIKE_RECEIVED(3, 30, 10),	// 좋아요 받기 (글당 10개 · 하루 30개)
	SNS_SHARE(100, 1, 0);		// SNS 공유 (사건당 1회 · 하루 1회)

	private final int amount;
	private final int dailyCap;
	private final int groupCap;

	XpSource(int amount, int dailyCap, int groupCap) {
		this.amount = amount;
		this.dailyCap = dailyCap;
		this.groupCap = groupCap;
	}

	public int amount() {
		return amount;
	}

	public int dailyCap() {
		return dailyCap;
	}

	public int groupCap() {
		return groupCap;
	}
}
