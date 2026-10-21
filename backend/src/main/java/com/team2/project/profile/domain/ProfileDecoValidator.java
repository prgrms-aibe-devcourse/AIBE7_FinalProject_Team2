package com.team2.project.profile.domain;

import static com.team2.project.profile.domain.ProfileDecoCatalog.SASH_SHORT;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.exception.FieldErrorDetail;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 꾸미기 저장 전 검사 (BE-96). 순수 함수라 DB 없이 테스트한다.
 * 1) 형식 · 값 목록 → 400 VALIDATION_ERROR  2) 잠김 → 409 DECO_LOCKED. 둘 다 details에 필드별 사유를 담고, 하나라도 걸리면 저장하지 않는다
 */
public final class ProfileDecoValidator {

	/** 대표 이미지 경로: <img src>에 그대로 들어가므로 /images/…/이름.webp 모양만 받는다(.. · // · 백슬래시 · 다른 문자 거절) */
	private static final Pattern PORTRAIT = Pattern.compile("^/images/[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*/[A-Za-z0-9_-]+\\.webp$");
	private static final int PORTRAIT_MAX_LENGTH = 200;	// V23 portrait 칼럼 길이

	static final String REQUIRED = "REQUIRED";
	static final String INVALID_FORMAT = "INVALID_FORMAT";
	static final String INVALID_VALUE = "INVALID_VALUE";

	private ProfileDecoValidator() {
	}

	public static void validate(ProfileDecoValues deco, Rewards unlocked) {
		checkValues(deco);
		checkLocks(deco, unlocked);
	}

	private static void checkValues(ProfileDecoValues deco) {
		List<FieldErrorDetail> errors = new ArrayList<>();
		if (deco.portrait() == null) {
			errors.add(new FieldErrorDetail("portrait", REQUIRED));
		} else if (deco.portrait().length() > PORTRAIT_MAX_LENGTH || !PORTRAIT.matcher(deco.portrait()).matches()) {
			errors.add(new FieldErrorDetail("portrait", INVALID_FORMAT));
		}
		oneOf(errors, "frame", deco.frame(), ProfileDecoCatalog.FRAMES);
		oneOf(errors, "background", deco.background(), ProfileDecoCatalog.BACKGROUNDS);
		oneOf(errors, "sashStyle", deco.sashStyle(), ProfileDecoCatalog.SASH_STYLES);
		oneOf(errors, "sashColorShort", deco.sashColorShort(), ProfileDecoCatalog.SASH_COLORS.get(SASH_SHORT));
		oneOf(errors, "sashColorLong", deco.sashColorLong(), ProfileDecoCatalog.SASH_COLORS.get(ProfileDecoCatalog.SASH_LONG));
		oneOf(errors, "sashIconLeft", deco.sashIconLeft(), ProfileDecoCatalog.SASH_ICONS);
		oneOf(errors, "sashIconRight", deco.sashIconRight(), ProfileDecoCatalog.SASH_ICONS);
		oneOf(errors, "bookmarkCharacter", deco.bookmarkCharacter(), ProfileDecoCatalog.BOOKMARK_CHARACTERS);
		oneOf(errors, "bookmarkFace", deco.bookmarkFace(), ProfileDecoCatalog.BOOKMARK_FACES);
		oneOf(errors, "bookmarkFaceStyle", deco.bookmarkFaceStyle(), ProfileDecoCatalog.BOOKMARK_FACE_STYLES);
		oneOf(errors, "bookmarkColor", deco.bookmarkColor(), ProfileDecoCatalog.BOOKMARK_COLORS);
		if (!errors.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), null, errors);
		}
	}

	private static void oneOf(List<FieldErrorDetail> errors, String field, String value, List<String> allowed) {
		if (value == null) {
			errors.add(new FieldErrorDetail(field, REQUIRED));
		} else if (!allowed.contains(value)) {
			errors.add(new FieldErrorDetail(field, INVALID_VALUE));
		}
	}

	/**
	 * 지금 화면에 쓰이는 값만 잠김을 본다: 카드 책갈피 색은 고른 모양의 것만, 좌우 아이콘은 짧은 책갈피일 때만.
	 * (쓰이지 않는 칸은 기본값이 잠겨 있을 수 있다 — 예: 긴 띠 색 기본 wine은 첫 판결 전까지 잠김. 모양을 바꿔 저장할 때 다시 검사된다)
	 * 책갈피 캐릭터 · 얼굴 · 리본 색은 잠금이 없다
	 */
	private static void checkLocks(ProfileDecoValues deco, Rewards unlocked) {
		List<FieldErrorDetail> locked = new ArrayList<>();
		String reason = ErrorCode.DECO_LOCKED.name();
		if (!ProfileDecoCatalog.DEFAULT_PORTRAITS.contains(deco.portrait())
			&& !unlocked.kinds().contains(ProfileDecoCatalog.kindOf(deco.portrait()))) {
			locked.add(new FieldErrorDetail("portrait", reason));
		}
		if (!unlocked.frames().contains(deco.frame())) {
			locked.add(new FieldErrorDetail("frame", reason));
		}
		if (!unlocked.backgrounds().contains(deco.background())) {
			locked.add(new FieldErrorDetail("background", reason));
		}
		if (!unlocked.sashStyles().contains(deco.sashStyle())) {
			locked.add(new FieldErrorDetail("sashStyle", reason));
		}
		switch (deco.sashStyle()) {
			case ProfileDecoCatalog.SASH_SHORT -> {
				if (!unlocked.sashColors().contains(ProfileDecoCatalog.sashColorKey(SASH_SHORT, deco.sashColorShort()))) {
					locked.add(new FieldErrorDetail("sashColorShort", reason));
				}
				if (!unlocked.sashIcons().contains(deco.sashIconLeft())) {
					locked.add(new FieldErrorDetail("sashIconLeft", reason));
				}
				if (!unlocked.sashIcons().contains(deco.sashIconRight())) {
					locked.add(new FieldErrorDetail("sashIconRight", reason));
				}
			}
			case ProfileDecoCatalog.SASH_LONG -> {
				if (!unlocked.sashColors().contains(
					ProfileDecoCatalog.sashColorKey(ProfileDecoCatalog.SASH_LONG, deco.sashColorLong()))) {
					locked.add(new FieldErrorDetail("sashColorLong", reason));
				}
			}
			default -> {
				// 없음: 색 · 아이콘을 쓰지 않는다
			}
		}
		if (!locked.isEmpty()) {
			throw new BusinessException(ErrorCode.DECO_LOCKED, ErrorCode.DECO_LOCKED.getMessage(), null, locked);
		}
	}
}
