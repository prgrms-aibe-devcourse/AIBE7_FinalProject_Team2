package com.team2.project.profile.repository;

import com.team2.project.profile.domain.AchievementUnlock;
import com.team2.project.profile.domain.AchievementUnlockId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AchievementUnlockRepository extends JpaRepository<AchievementUnlock, AchievementUnlockId> {

	/**
	 * 해금 기록 한 줄. 이미 있으면(동시에 두 번 판정 · 다시 조회) 아무것도 하지 않아 처음 해금 시각이 남는다
	 * @return 새로 넣은 행 수 (0이면 이미 기록됨)
	 */
	@Modifying
	@Query(value = """
		insert into member_achievement_unlock (member_id, achievement_id, unlocked_at, seen_at)
		values (:memberId, :achievementId, :unlockedAt, :seenAt)
		on conflict (member_id, achievement_id) do nothing
		""", nativeQuery = true)
	int insertIfAbsent(@Param("memberId") long memberId, @Param("achievementId") String achievementId,
		@Param("unlockedAt") Instant unlockedAt, @Param("seenAt") Instant seenAt);

	List<AchievementUnlock> findByIdMemberId(long memberId);

	/** 도전과제 "봤음". 이미 봤거나 기록이 없는 id는 건너뛴다 (멱등) */
	@Modifying
	@Query("update AchievementUnlock a set a.seenAt = :now "
		+ "where a.id.memberId = :memberId and a.id.achievementId in :achievementIds and a.seenAt is null")
	int markSeen(@Param("memberId") long memberId,
		@Param("achievementIds") Collection<String> achievementIds, @Param("now") Instant now);
}
