package com.team2.project.personality.repository;

import com.team2.project.personality.domain.ExperiencePersonality;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperiencePersonalityRepository extends JpaRepository<ExperiencePersonality, Long> {

	Optional<ExperiencePersonality> findByExperience_Id(Long experienceId);

	boolean existsByExperience_Id(Long experienceId);

	/** 종합 성향 계산용: 사용자의 첫 회차 체험 기여만 (attempt_no = 1, 성향 산식 3-1) */
	@Query("SELECT p FROM ExperiencePersonality p JOIN FETCH p.experience e JOIN FETCH e.legalCase "
		+ "WHERE e.anonymousUser.id = :anonymousUserId AND e.attemptNo = 1 ORDER BY p.createdAt, p.id")
	List<ExperiencePersonality> findFirstAttemptsByAnonymousUser(@Param("anonymousUserId") UUID anonymousUserId);
}
