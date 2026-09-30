package com.team2.project.experience.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.team2.project.experience.domain.AnonymousUser;

public interface AnonymousUserRepository extends JpaRepository<AnonymousUser, UUID> {

	/** last_seen_at을 갱신하고 갱신된 행 수를 돌려준다. 0이면 DB에 없는 익명 ID다. */
	@Modifying
	@Query("update AnonymousUser u set u.lastSeenAt = :now where u.id = :id")
	int touch(UUID id, OffsetDateTime now);
}
