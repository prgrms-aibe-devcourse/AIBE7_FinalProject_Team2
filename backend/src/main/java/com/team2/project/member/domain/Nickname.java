package com.team2.project.member.domain;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import java.util.regex.Pattern;

/**
 * 닉네임 형식 (BE-113 · BE-139). 한글 · 영문 · 숫자 2 ~ 12자, 한글이 있으면 8자까지
 * (frontend/src/utils/nickname.js와 같게). 공백 · 기호는 없다 — 커뮤니티의 자동 이름 '방청객 {회원 ID}'(공백 포함)와 겹칠 수 없다
 */
public final class Nickname {

	private static final Pattern FORMAT = Pattern.compile("[가-힣A-Za-z0-9]{2,12}");
	private static final Pattern HANGUL = Pattern.compile("[가-힣]");
	private static final int MAX_WITH_HANGUL = 8;

	private Nickname() {
	}

	/** 형식에 맞지 않으면 400 VALIDATION_ERROR(details: nickname) */
	public static void validate(String nickname) {
		if (nickname == null || !FORMAT.matcher(nickname).matches()
			|| (HANGUL.matcher(nickname).find() && nickname.length() > MAX_WITH_HANGUL)) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "nickname");
		}
	}
}
