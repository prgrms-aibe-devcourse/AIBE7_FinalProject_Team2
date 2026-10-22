package com.team2.project.profile.domain;

import static com.team2.project.profile.domain.ProfileDecoCatalog.SASH_SHORT;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.exception.FieldErrorDetail;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 꾸미기 저장 전 검사 (BE-96). 순수 함수라 DB 없이 테스트한다.
 * 1) 형식 · 값 목록 → 400 VALIDATION_ERROR  2) 잠김 → 409 DECO_LOCKED. 둘 다 details에 필드별 사유를 담고, 하나라도 걸리면 저장하지 않는다
 * 잠김은 이미 저장해 둔 값을 다시 거절하지 않는다(BE-98): 표가 바뀌어 저장값이 잠겨도 다른 칸은 저장할 수 있어야 한다
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

	/** 저장 기록이 없을 때(처음 저장): 쓰이는 칸을 모두 잠김 검사한다 */
	public static void validate(ProfileDecoValues deco, Rewards unlocked) {
		validate(deco, unlocked, null);
	}

	/**
	 * @param saved 지금 저장된 꾸미기(없으면 null). 형식 · 값 목록은 늘 전체를 보고, 잠김은 저장값과 같은 칸을 건너뛴다
	 */
	public static void validate(ProfileDecoValues deco, Rewards unlocked, ProfileDecoValues saved) {
		checkValues(deco);
		checkLocks(deco, unlocked, saved);
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
	private static void checkLocks(ProfileDecoValues deco, Rewards unlocked, ProfileDecoValues saved) {
		List<FieldErrorDetail> found = new ArrayList<>();
		String reason = ErrorCode.DECO_LOCKED.name();
		// 저장값과 같고 저장할 때도 쓰이던 칸(그때 잠김 검사를 통과한 칸)은 다시 거절하지 않는다
		LockList locked = new LockList(found, deco, saved);
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
		if (!found.isEmpty()) {
			throw new BusinessException(ErrorCode.DECO_LOCKED, ErrorCode.DECO_LOCKED.getMessage(), null, found);
		}
	}

	/**
	 * 잠긴 칸 목록. 저장값과 같은 칸을 건너뛰되, 저장할 때 쓰이지 않던 칸(예: 책갈피 없음일 때의 짧은 책갈피 색)은 그때 잠김 검사를
	 * 받지 않았으므로 건너뛰지 않는다 — 안 쓰이는 칸에 잠긴 값을 넣어 두었다가 모양만 바꿔 여는 것을 막는다
	 */
	private record LockList(List<FieldErrorDetail> found, ProfileDecoValues deco, ProfileDecoValues saved) {

		void add(FieldErrorDetail detail) {
			String field = detail.field();
			if (saved != null && activeIn(saved, field) && Objects.equals(valueOf(saved, field), valueOf(deco, field))) {
				return;
			}
			found.add(detail);
		}
	}

	/** 그 꾸미기에서 이 칸이 화면에 쓰이는지 (잠김 검사 대상인지) */
	private static boolean activeIn(ProfileDecoValues deco, String field) {
		return switch (field) {
			case "sashColorShort", "sashIconLeft", "sashIconRight" -> SASH_SHORT.equals(deco.sashStyle());
			case "sashColorLong" -> ProfileDecoCatalog.SASH_LONG.equals(deco.sashStyle());
			default -> true;
		};
	}

	private static String valueOf(ProfileDecoValues deco, String field) {
		return switch (field) {
			case "portrait" -> deco.portrait();
			case "frame" -> deco.frame();
			case "background" -> deco.background();
			case "sashStyle" -> deco.sashStyle();
			case "sashColorShort" -> deco.sashColorShort();
			case "sashColorLong" -> deco.sashColorLong();
			case "sashIconLeft" -> deco.sashIconLeft();
			case "sashIconRight" -> deco.sashIconRight();
			default -> throw new IllegalArgumentException("잠김 검사 대상이 아닌 칸: " + field);
		};
	}
}
