package com.team2.project.legalcase.domain;

/**
 * 사용자 쪽 대본 상태 (BE-126 2차-2, API 1 · 16 {@code scriptStatus}, 커뮤니티 메인 C-6). 검수 상태(승인 · 반려 · 대기)는 공개 API에
 * 내보내지 않고(BE-116), 화면이 쓰는 "지금 체험할 수 있는가 · 왜 못 하는가"만 셋으로 알린다.
 *
 * <ul>
 *   <li>{@link #READY} — 공개 대본이 있다({@link CaseScript#isPublic()}). API 18이 200을 준다</li>
 *   <li>{@link #MAINTENANCE} — 한 번 사용자에게 나간 대본({@link CaseScript#getReleasedAt()})인데 블록 수정 · 반려 · 재생성 반영으로
 *       승인이 풀렸다. API 18이 503 {@code SCRIPT_UNDER_MAINTENANCE}를 준다</li>
 *   <li>{@link #PREPARING} — 대본이 없거나 아직 한 번도 나가지 않았다. API 18이 404 {@code SCRIPT_NOT_FOUND}를 준다</li>
 * </ul>
 *
 * <p>호환용 {@code scriptReady}는 {@code scriptStatus == READY}다.
 */
public enum ScriptStatus {
	READY,
	PREPARING,
	MAINTENANCE;

	/**
	 * @param isPublic 공개 대본인지 ({@link CaseScript#isPublic()})
	 * @param released 한 번 나간 대본인지 ({@link CaseScript#getReleasedAt()}가 있다)
	 */
	public static ScriptStatus of(boolean isPublic, boolean released) {
		if (isPublic) {
			return READY;
		}
		return released ? MAINTENANCE : PREPARING;
	}

	/** 대본 행이 없으면 null을 넘긴다 — 준비 중이다 */
	public static ScriptStatus of(CaseScript script) {
		return script == null ? PREPARING : of(script.isPublic(), script.getReleasedAt() != null);
	}
}
