package com.team2.project.profile.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 프로필 선택 설정 (BE-104, V29 · cardLine · cardLevel은 BE-114, V36). 익명 사용자당 한 줄. 보낸 칸만 바꾼다(PATCH) — 검증은 ProfileSettingsPatch가 먼저 한다 */
@Entity
@Table(name = "profile_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileSetting {

	@Id
	private UUID anonymousUserId;

	private String shownCard;
	private String profileCardType;
	private String bio;
	private String cardLine;
	private boolean cardLevel;

	private Instant updatedAt;

	/** 처음 저장: 기본값에서 시작해 보낸 칸만 바꾼다 */
	public static ProfileSetting of(UUID anonymousUserId, ProfileSettingsPatch patch, Instant now) {
		ProfileSetting setting = new ProfileSetting();
		setting.anonymousUserId = anonymousUserId;
		setting.shownCard = ProfileSettingsValues.DEFAULTS.shownCard();
		setting.profileCardType = ProfileSettingsValues.DEFAULTS.profileCardType();
		setting.bio = ProfileSettingsValues.DEFAULTS.bio();
		setting.cardLine = ProfileSettingsValues.DEFAULTS.cardLine();
		setting.cardLevel = ProfileSettingsValues.DEFAULTS.cardLevel();
		setting.apply(patch, now);
		return setting;
	}

	/** 보낸 칸만 바꾼다 (PATCH) */
	public void apply(ProfileSettingsPatch patch, Instant now) {
		patch.shownCard().ifPresent(value -> this.shownCard = value);
		patch.profileCardType().ifPresent(value -> this.profileCardType = value);
		patch.bio().ifPresent(value -> this.bio = value);
		patch.cardLine().ifPresent(value -> this.cardLine = value);
		patch.cardLevel().ifPresent(value -> this.cardLevel = value);
		this.updatedAt = now;
	}

	public ProfileSettingsValues values() {
		return new ProfileSettingsValues(shownCard, profileCardType, bio, cardLine, cardLevel);
	}
}
