package com.team2.project.member.domain;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import java.util.regex.Pattern;

/**
 * 닉네임 형식 (BE-113 · BE-139). 한글 · 영문 · 숫자 2 ~ 12자, 한글이 있으면 8자까지
 * (frontend/src/utils/nickname.js와 같게). 공백 · 기호는 없다 — 커뮤니티의 자동 이름 '방청객 {회원 ID}'(공백 포함)와 겹칠 수 없다.
 * 규칙은 이 클래스 한 곳에 두고, 가입(M-5)은 요청 DTO가 같은 길이 · 글자 검사를 먼저 한다. 거절 사유(details.reason)는 DTO 검사와 같은 모양의 이름이다(BE-134)
 */
public final class Nickname {

	/** 자동 이름 `방청객 {회원 ID}`를 흉내 내지 못하게 이 말로 시작하는 닉네임은 쓸 수 없다 (PR #343 리뷰). 금칙어 기능(A-39 ~ A-41)이 생기면 거기로 옮긴다 */
	private static final String RESERVED_PREFIX = "방청객";

	private static final Pattern LETTERS = Pattern.compile("[가-힣A-Za-z0-9]+");
	private static final int MIN = 2;
	private static final int MAX = 12;
	private static final Pattern HANGUL = Pattern.compile("[가-힣]");
	private static final int MAX_WITH_HANGUL = 8;

	private Nickname() {
	}

	/** 닉네임이 없는 회원(카카오)의 표시 이름 앞말. 뒤에 회원 번호를 붙여 사람마다 다르고 바뀌지 않는다 (BE-115) */
	public static final String AUTO_NAME_PREFIX = "방청객 ";

	/** 화면 · 커뮤니티에 보이는 이름: 닉네임이 있으면 그 값, 없으면 자동 이름 `방청객 {회원 ID}` */
	public static String displayName(long memberId, String nickname) {
		return nickname == null || nickname.isBlank() ? AUTO_NAME_PREFIX + memberId : nickname;
	}

	/** 거절 사유(details.reason). 앞의 셋은 요청 DTO 검사(@NotBlank · @Pattern · @Size)와 같은 이름이다 */
	public static final String NOT_BLANK = "NotBlank";
	public static final String PATTERN = "Pattern";
	public static final String SIZE = "Size";
	/** 한글이 들어가면 8자까지 — 길이 규칙이 글자 종류에 따라 달라 DTO의 @Size로는 표현하지 못한다 */
	public static final String SIZE_WITH_HANGUL = "SizeWithHangul";
	/** '방청객'으로 시작 */
	public static final String RESERVED = "Reserved";

	/** 형식에 맞지 않으면 400 VALIDATION_ERROR(details: nickname · 사유). 사유는 아래 순서로 처음 걸린 하나 */
	public static void validate(String nickname) {
		String reason = violation(nickname);
		if (reason != null) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "nickname", reason);
		}
	}

	/** 처음 걸린 규칙의 사유, 괜찮으면 null. 글자 → 길이 → 한글 길이 → 예약어 순서다(화면 nicknameError와 같은 순서로 고칠 곳을 알린다) */
	static String violation(String nickname) {
		if (nickname == null || nickname.isBlank()) {
			return NOT_BLANK;
		}
		if (!LETTERS.matcher(nickname).matches()) {
			return PATTERN;
		}
		if (nickname.length() < MIN || nickname.length() > MAX) {
			return SIZE;
		}
		if (HANGUL.matcher(nickname).find() && nickname.length() > MAX_WITH_HANGUL) {
			return SIZE_WITH_HANGUL;
		}
		if (nickname.startsWith(RESERVED_PREFIX)) {
			return RESERVED;
		}
		return null;
	}
}
