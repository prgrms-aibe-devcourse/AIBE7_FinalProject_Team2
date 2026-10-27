package com.team2.project.level.domain;

/**
 * 이벤트로 받는 경험치 수급처 (BE-106). 사건 완료 · 성향 테스트 · 출석 · 업적은 기존 테이블에서 계산하므로 여기에 없다 (LevelXp).
 * 같은 (사용자, 수급처, 대상)은 한 번만 준다(xp_event 유니크). 상한 0은 "상한 없음"
 *
 * <p>호출하는 쪽(커뮤니티 BE)은 수급처별로 아래 ref_key · group_key를 써야 한다. ref_key를 잘못 잡으면 중복 판정이 어긋나고,
 * 특히 LIKE_RECEIVED의 ref_key를 글 ID만으로 두면 글당 1번만 지급되어 group_cap(글당 10개)이 의미가 없어진다.
 * <pre>
 * 수급처         ref_key(지급 대상)        group_key(묶음 상한)
 * POST           글 ID                     빈 문자열
 * COMMENT        댓글 ID                   빈 문자열
 * LIKE_GIVEN     글 ID                     빈 문자열
 * LIKE_RECEIVED  "글 ID:누른 사람 ID"      글 ID
 * SHARE_POST     사건 ID                   빈 문자열
 * SNS_SHARE      사건 ID                   빈 문자열
 * </pre>
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
