package com.team2.project.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 도전과제 해금 기록 기본 키: 회원 × 도전과제 id (V41) */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AchievementUnlockId implements Serializable {

	@Column(name = "member_id")
	private Long memberId;

	@Column(name = "achievement_id")
	private String achievementId;

	public AchievementUnlockId(Long memberId, String achievementId) {
		this.memberId = memberId;
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
		return Objects.equals(memberId, other.memberId) && Objects.equals(achievementId, other.achievementId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(memberId, achievementId);
	}
}
