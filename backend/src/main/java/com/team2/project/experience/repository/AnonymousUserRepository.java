package com.team2.project.experience.repository;

import com.team2.project.experience.domain.AnonymousUser;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnonymousUserRepository extends JpaRepository<AnonymousUser, UUID> {

	/** last_seen_at을 갱신하고 갱신된 행 수를 돌려준다. 0이면 DB에 없는 익명 ID다 (조회 없이 존재 확인 겸 갱신) */
	@Modifying
	@Query("update AnonymousUser u set u.lastSeenAt = :now where u.id = :id")
	int touch(@Param("id") UUID id, @Param("now") Instant now);
}
