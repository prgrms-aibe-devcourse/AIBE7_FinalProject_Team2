package com.team2.project.personality.repository;

import com.team2.project.personality.domain.PersonalityResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalityResultRepository extends JpaRepository<PersonalityResult, Long> {

	/** 사용자의 현재 결과 (없으면 테스트 전) */
	Optional<PersonalityResult> findByAnonymousUser_IdAndCurrentTrue(UUID anonymousUserId);

	/**
	 * 재응시 전에 이전 현재 결과를 내린다(이력은 남긴다). 새 결과 저장과 같은 트랜잭션에서 먼저 부른다 —
	 * 부분 유니크(사용자마다 현재 결과 1건)는 문장마다 검사하므로 순서가 바뀌면 위반이 난다
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE PersonalityResult r SET r.current = false WHERE r.anonymousUser.id = :anonymousUserId AND r.current = true")
	int clearCurrent(@Param("anonymousUserId") UUID anonymousUserId);
}
