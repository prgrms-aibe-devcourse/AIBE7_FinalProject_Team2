package com.team2.project.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.exception.FieldErrorDetail;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 꾸미기 저장 검사 (BE-96) */
class ProfileDecoValidatorTest {

	private static final ProfileDecoValues D = ProfileDecoCatalog.DEFAULTS;
	private static final Rewards FREE = ProfileDecoCatalog.FREE;

	private static ProfileDecoValues with(String portrait, String frame, String sashStyle, String colorShort,
		String colorLong, String iconLeft, String iconRight) {
		return new ProfileDecoValues(portrait, frame, D.background(), sashStyle, colorShort, colorLong, iconLeft, iconRight,
			D.bookmarkCharacter(), D.bookmarkFace(), D.bookmarkFaceStyle(), D.bookmarkColor());
	}

	private static ProfileDecoValues portrait(String portrait) {
		return with(portrait, D.frame(), D.sashStyle(), D.sashColorShort(), D.sashColorLong(), D.sashIconLeft(),
			D.sashIconRight());
	}

	private static BusinessException fail(ProfileDecoValues deco, Rewards unlocked) {
		return catchThrowableOfType(BusinessException.class, () -> ProfileDecoValidator.validate(deco, unlocked));
	}

	@Test
	@DisplayName("기본값은 처음부터 열린 것만으로 저장할 수 있다")
	void defaultsPass() {
		assertThatCode(() -> ProfileDecoValidator.validate(D, FREE)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = {"/images/../secret.webp", "/images/cases/../a.webp", "/images//cases/a.webp",
		"/images/cases\\a.webp", "/images/cases/a.png", "images/cases/a.webp", "/images/cases/a.webp?x=1",
		"/images/a.webp", "https://evil.example/images/cases/a.webp", "/images/cases/a b.webp", "javascript:alert(1)"})
	@DisplayName("대표 이미지 경로 형식이 틀리면 400 VALIDATION_ERROR (portrait INVALID_FORMAT)")
	void portraitFormat(String path) {
		BusinessException e = fail(portrait(path), FREE);

		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
		assertThat(e.getDetails()).containsExactly(new FieldErrorDetail("portrait", "INVALID_FORMAT"));
	}

	@Test
	@DisplayName("값이 없거나 목록에 없으면 400 (REQUIRED · INVALID_VALUE), 잠김 검사보다 먼저")
	void valueList() {
		BusinessException e = fail(with(D.portrait(), "rainbow", D.sashStyle(), D.sashColorShort(), null,
			D.sashIconLeft(), D.sashIconRight()), FREE);

		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
		assertThat(e.getDetails()).containsExactlyInAnyOrder(new FieldErrorDetail("frame", "INVALID_VALUE"),
			new FieldErrorDetail("sashColorLong", "REQUIRED"));
	}

	@Test
	@DisplayName("잠긴 항목이 있으면 409 DECO_LOCKED, 잠긴 필드를 모두 담는다")
	void lockedFields() {
		BusinessException e = fail(with("/images/cases/case-1-origin.webp", "wood", "short", "green", D.sashColorLong(),
			"law", "star"), FREE);

		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DECO_LOCKED);
		assertThat(e.getDetails()).extracting(FieldErrorDetail::field)
			.containsExactlyInAnyOrder("portrait", "frame", "sashColorShort", "sashIconLeft");
	}

	@Test
	@DisplayName("기본 대표 이미지 두 장은 그 종류가 잠겨 있어도 고를 수 있다")
	void defaultPortraitAlwaysAllowed() {
		Rewards noStanding = new Rewards(List.of(), FREE.frames(), FREE.backgrounds(), FREE.sashStyles(),
			FREE.sashColors(), FREE.sashIcons());

		assertThatCode(() -> ProfileDecoValidator.validate(portrait("/images/characters/standing/nambul.webp"), noStanding))
			.doesNotThrowAnyException();
		assertThat(fail(portrait("/images/characters/standing/law-book.webp"), noStanding).getErrorCode())
			.isEqualTo(ErrorCode.DECO_LOCKED);
	}

	@Test
	@DisplayName("지금 쓰이지 않는 칸은 잠김을 보지 않는다 (고르지 않은 모양의 색 · 짧은 책갈피가 아닐 때 아이콘)")
	void inactiveFieldsIgnored() {
		// 책갈피 없음: 긴 띠 색 gold · 아이콘 law는 잠겨 있지만 쓰이지 않는다
		assertThatCode(() -> ProfileDecoValidator.validate(with(D.portrait(), "basic", "none", "wine", "gold", "law", "law"),
			FREE)).doesNotThrowAnyException();
		// 짧은 책갈피: 긴 띠의 기본색 wine(첫 판결 전 잠김)은 보지 않는다
		assertThatCode(() -> ProfileDecoValidator.validate(with(D.portrait(), "basic", "short", "wine", "wine", "star",
			"none"), FREE)).doesNotThrowAnyException();
	}
}
