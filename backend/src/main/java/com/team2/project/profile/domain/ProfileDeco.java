package com.team2.project.profile.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 프로필 꾸미기 설정 (BE-96, V23 → BE-140 V41 member_profile_deco). 회원당 한 줄. 검증은 ProfileDecoValidator가 저장 전에 한다 */
@Entity
@Table(name = "member_profile_deco")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileDeco {

	@Id
	private Long memberId;

	private String portrait;
	private String frame;
	private String background;
	private String sashStyle;
	private String sashColorShort;
	private String sashColorLong;
	private String sashIconLeft;
	private String sashIconRight;
	private String bookmarkCharacter;
	private String bookmarkFace;
	private String bookmarkFaceStyle;
	private String bookmarkColor;

	private Instant updatedAt;	// 로그인 이관 때 가장 최근 꾸미기를 고르는 기준

	public static ProfileDeco of(long memberId, ProfileDecoValues values, Instant now) {
		ProfileDeco deco = new ProfileDeco();
		deco.memberId = memberId;
		deco.apply(values, now);
		return deco;
	}

	/** 전체를 바꾼다 (PUT) */
	public void apply(ProfileDecoValues values, Instant now) {
		this.portrait = values.portrait();
		this.frame = values.frame();
		this.background = values.background();
		this.sashStyle = values.sashStyle();
		this.sashColorShort = values.sashColorShort();
		this.sashColorLong = values.sashColorLong();
		this.sashIconLeft = values.sashIconLeft();
		this.sashIconRight = values.sashIconRight();
		this.bookmarkCharacter = values.bookmarkCharacter();
		this.bookmarkFace = values.bookmarkFace();
		this.bookmarkFaceStyle = values.bookmarkFaceStyle();
		this.bookmarkColor = values.bookmarkColor();
		this.updatedAt = now;
	}

	public ProfileDecoValues values() {
		return new ProfileDecoValues(portrait, frame, background, sashStyle, sashColorShort, sashColorLong, sashIconLeft,
			sashIconRight, bookmarkCharacter, bookmarkFace, bookmarkFaceStyle, bookmarkColor);
	}
}
