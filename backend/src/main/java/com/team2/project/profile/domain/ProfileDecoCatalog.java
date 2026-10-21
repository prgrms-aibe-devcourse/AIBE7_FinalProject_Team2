package com.team2.project.profile.domain;

import java.util.List;
import java.util.Map;

/**
 * 프로필 꾸미기 · 도전과제 표 (BE-96). 서버가 원본이고, 프론트는 id → 제목 · 문구 · 그림만 둔다.
 * 처음 값은 FE-68 profileDeco.js · bookmarkCharacter.js(2026-10-21 develop)에서 옮겼다. 표가 아직 임시안이라 이 파일 한 곳에 모은다.
 * 바꾸면 API 명세 P-5 · P-7 설명과 테스트(ProfileDecoCatalogTest)를 함께 본다
 */
public final class ProfileDecoCatalog {

	private ProfileDecoCatalog() {
	}

	// ── 값 목록 (목록에 없으면 VALIDATION_ERROR) ──
	public static final List<String> FRAMES = List.of("basic", "wood", "gold", "navy", "stitch");
	public static final List<String> BACKGROUNDS = List.of("cream", "latte", "tarot", "news", "linen");
	/** 카드 책갈피 모양. none이면 색 · 아이콘을 쓰지 않는다 */
	public static final List<String> SASH_STYLES = List.of("none", "long", "short");
	public static final String SASH_NONE = "none";
	public static final String SASH_SHORT = "short";
	public static final String SASH_LONG = "long";
	/** 모양마다 따로 있는 색 표 */
	public static final Map<String, List<String>> SASH_COLORS = Map.of(
		SASH_SHORT, List.of("wine", "navy", "gold", "green"),
		SASH_LONG, List.of("wine", "navy", "gold", "green"));
	/** 짧은 책갈피 좌우 아이콘 */
	public static final List<String> SASH_ICONS = List.of("none", "star", "scale", "gavel", "cup", "heart", "naero", "nambul", "law");
	public static final String ICON_NONE = "none";

	// 책갈피 캐릭터 (잠금 없음, 값 목록만 검사)
	public static final List<String> BOOKMARK_CHARACTERS = List.of("naero", "nambul", "law-scale", "law-gavel", "law-book");
	public static final List<String> BOOKMARK_FACES = List.of("cheer", "happy", "neutral", "sorry", "surprised", "thinking");
	public static final List<String> BOOKMARK_FACE_STYLES = List.of("generated", "cropped");
	public static final List<String> BOOKMARK_COLORS = List.of("bronze", "wine", "navy", "green", "gold");

	/** 기본 대표 이미지 두 장: 잠금과 상관없이 늘 고를 수 있다 */
	public static final List<String> DEFAULT_PORTRAITS = List.of(
		"/images/characters/standing/naero.webp", "/images/characters/standing/nambul.webp");

	/** 저장 기록이 없을 때의 꾸미기 (FE-68 DEFAULT_DECO · 책갈피 기본값) */
	public static final ProfileDecoValues DEFAULTS = new ProfileDecoValues(
		DEFAULT_PORTRAITS.get(0), "basic", "cream", SASH_NONE, "wine", "wine", ICON_NONE, ICON_NONE,
		"naero", "happy", "generated", "bronze");

	/** 처음부터 열린 것 (FE-68 FREE) */
	public static final Rewards FREE = Rewards.builder()
		.kinds("characters/roles", "characters/standing").frames("basic").backgrounds("cream")
		.sashStyles("none", "short").sashColors("short:wine").sashIcons("none", "star").build();

	/** 도전과제 (FE-68 ACHIEVEMENTS와 같은 순서) */
	public static final List<Achievement> ACHIEVEMENTS = List.of(
		new Achievement("first-case", AchievementStat.COMPLETED, 1, Rewards.builder()
			.kinds("characters/scene").frames("wood").sashStyles("long").sashColors("long:wine").build()),
		new Achievement("personality", AchievementStat.HAS_TYPE, 1, Rewards.builder()
			.kinds("characters/personality").backgrounds("tarot").sashIcons("scale").build()),
		new Achievement("attend-3", AchievementStat.ATTENDANCE_DAYS, 3, Rewards.builder()
			.kinds("characters/icon/generated").backgrounds("latte").sashIcons("cup").build()),
		new Achievement("collect-5", AchievementStat.COLLECTED, 5, Rewards.builder()
			.kinds("cases").backgrounds("news").sashIcons("naero", "nambul").build()),
		new Achievement("attend-7", AchievementStat.ATTENDANCE_DAYS, 7, Rewards.builder()
			.kinds("characters/cheek", "characters/cheek-slim", "characters/deform").frames("gold").sashIcons("heart").build()),
		new Achievement("cases-3", AchievementStat.COMPLETED, 3, Rewards.builder()
			.frames("stitch").backgrounds("linen").sashColors("short:navy", "long:navy").sashIcons("gavel").build()),
		new Achievement("collect-20", AchievementStat.COLLECTED, 20, Rewards.builder()
			.kinds("characters/law", "characters/adult").sashColors("short:green", "long:green").build()),
		new Achievement("attend-30", AchievementStat.ATTENDANCE_DAYS, 30, Rewards.builder()
			.kinds("characters/mascot").frames("navy").sashColors("short:gold", "long:gold").sashIcons("law").build()));

	/** 카드 책갈피 색의 해금 키: "모양:색" */
	public static String sashColorKey(String style, String color) {
		return style + ":" + color;
	}

	/**
	 * 대표 이미지 경로의 종류(폴더): "/images/characters/scene/a.webp" → "characters/scene" (프론트 kindOfPortrait와 같은 규칙).
	 * 형식 검사(PortraitPath.isValid)를 통과한 경로에만 쓴다
	 */
	public static String kindOf(String portrait) {
		return portrait.substring("/images/".length(), portrait.lastIndexOf('/'));
	}
}
