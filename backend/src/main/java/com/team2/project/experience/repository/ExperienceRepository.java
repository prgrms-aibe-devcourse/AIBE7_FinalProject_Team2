package com.team2.project.experience.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.team2.project.experience.domain.Experience;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {

	/** 사용자의 가장 최근 회차 체험 (API 1-3 "내 체험" 규칙). */
	Optional<Experience> findFirstByAnonymousUserIdAndCaseIdOrderByAttemptNoDesc(UUID anonymousUserId, Long caseId);

	/** 사건별 참여자 수: attempt_no = 1이고 COMPLETED인 체험 수 (API 1 participantCount). */
	@Query("""
			select e.caseId as caseId, count(e) as participantCount
			from Experience e
			where e.attemptNo = 1 and e.status = com.team2.project.experience.domain.ExperienceStatus.COMPLETED
			group by e.caseId
			""")
	List<CaseParticipantCount> countCompletedFirstAttempts();
}
