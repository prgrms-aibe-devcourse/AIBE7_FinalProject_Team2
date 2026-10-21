package com.team2.project.profile.domain;

/**
 * 꾸미기 값 한 벌 (BE-96). 저장 · 조회 · PUT 요청이 같은 모양을 쓴다(필드 이름은 FE-68 getProfileDeco와 같다)
 */
public record ProfileDecoValues(
	String portrait,
	String frame,
	String background,
	String sashStyle,
	String sashColorShort,
	String sashColorLong,
	String sashIconLeft,
	String sashIconRight,
	String bookmarkCharacter,
	String bookmarkFace,
	String bookmarkFaceStyle,
	String bookmarkColor) {
}
