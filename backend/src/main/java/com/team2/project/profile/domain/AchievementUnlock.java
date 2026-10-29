package com.team2.project.profile.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 도전과제 해금 기록 (BE-83, V35). 판정은 지금처럼 계산하고, 새로 CLEARED가 된 것만 남긴다.
 * 기록이 있으면 표가 바뀌어 다시 미달이 되어도 CLEARED를 유지한다(AchievementJudge).
 * 저장은 AchievementUnlockRepository.insertIfAbsent로 한다(동시에 두 번 판정돼도 한 줄)
 */
@Entity
@Table(name = "achievement_unlock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AchievementUnlock {

	@EmbeddedId
	private AchievementUnlockId id;

	private Instant unlockedAt;

	private Instant seenAt;	// null이면 "새로 열림"

	public UnlockMark mark() {
		return new UnlockMark(unlockedAt, seenAt != null);
	}
}
