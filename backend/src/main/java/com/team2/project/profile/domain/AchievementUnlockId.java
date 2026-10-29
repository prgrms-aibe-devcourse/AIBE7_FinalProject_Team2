package com.team2.project.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 도전과제 해금 기록 기본 키: 익명 사용자 × 도전과제 id (V35) */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AchievementUnlockId implements Serializable {

	@Column(name = "anonymous_user_id")
	private UUID anonymousUserId;

	@Column(name = "achievement_id")
	private String achievementId;

	public AchievementUnlockId(UUID anonymousUserId, String achievementId) {
		this.anonymousUserId = anonymousUserId;
		this.achievementId = achievementId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AchievementUnlockId other)) {
			return false;
		}
		return Objects.equals(anonymousUserId, other.anonymousUserId) && Objects.equals(achievementId, other.achievementId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(anonymousUserId, achievementId);
	}
}
