package com.team2.project.community.domain;

/**
 * 커뮤니티 글자 수 · 글자 검사 (BE-115). 화면 상수(communityMockApi.js TITLE_MAX · BODY_MAX · COMMENT_MAX)와 같다.
 * 글자 수는 앞뒤 공백을 뺀 뒤 유니코드 글자(코드 포인트) 기준이다 — DB varchar 길이와 같은 기준(BE-104 bio와 같다)
 */
public final class CommunityText {

	public static final int TITLE_MAX = 60;
	public static final int BODY_MAX = 2000;
	public static final int COMMENT_MAX = 500;

	private CommunityText() {
	}

	/** 제목: 1 ~ 60자, 줄바꿈 · 제어 문자 없음. 맞지 않으면 null */
	public static String title(Object value) {
		return clean(value, TITLE_MAX, false);
	}

	/** 본문 · 댓글: 1 ~ max자. 줄바꿈 · 탭은 두고 그 밖의 제어 문자(NUL 등)는 막는다. 맞지 않으면 null */
	public static String body(Object value, int max) {
		return clean(value, max, true);
	}

	private static String clean(Object value, int max, boolean multiline) {
		if (!(value instanceof String text)) {
			return null;
		}
		String stripped = text.strip();
		int length = stripped.codePointCount(0, stripped.length());
		if (length == 0 || length > max || stripped.codePoints().anyMatch(c -> forbidden(c, multiline))) {
			return null;
		}
		return stripped;
	}

	/** NUL은 PostgreSQL 문자열에 저장할 수 없어 걸러 내지 않으면 400이 아니라 500이 된다(PR #251 리뷰와 같은 이유) */
	private static boolean forbidden(int c, boolean multiline) {
		if (multiline && (c == '\n' || c == '\r' || c == '\t')) {
			return false;
		}
		return Character.isISOControl(c) || c == ' ' || c == ' ';
	}
}
