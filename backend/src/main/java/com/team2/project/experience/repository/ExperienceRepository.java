package com.team2.project.experience.repository;

import com.team2.project.experience.domain.Experience;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {

	/** 내 체험: 익명 ID + 사건의 가장 최근 회차 (API 명세 1-3, MVP는 항상 1회차) */
	Optional<Experience> findFirstByAnonymousUser_IdAndLegalCase_IdOrderByAttemptNoDesc(UUID anonymousUserId,
		Long caseId);

	default Optional<Experience> findLatest(UUID anonymousUserId, Long caseId) {
		return findFirstByAnonymousUser_IdAndLegalCase_IdOrderByAttemptNoDesc(anonymousUserId, caseId);
	}

	/** 참여자 수: 1회차이고 완료한 체험 수 (API 1 participantCount, 통계는 1회차만, DR-7) */
	default long countCompletedFirstAttempts(Long caseId) {
		return countCompletedByAttemptNo(caseId, Experience.FIRST_ATTEMPT);
	}

	@Query("select count(e) from Experience e where e.legalCase.id = :caseId and e.attemptNo = :attemptNo "
		+ "and e.status = com.team2.project.experience.domain.ExperienceStatus.COMPLETED")
	long countCompletedByAttemptNo(@Param("caseId") Long caseId, @Param("attemptNo") int attemptNo);

	/** 사건별 참여자 수를 한 번에 (API 1 사건 목록, 사건마다 세는 N+1을 막는다) */
	default List<CaseParticipantCount> countCompletedFirstAttemptsByCase() {
		return countCompletedByAttemptNoGroupByCase(Experience.FIRST_ATTEMPT);
	}

	@Query("select e.legalCase.id as caseId, count(e) as participantCount from Experience e "
		+ "where e.attemptNo = :attemptNo "
		+ "and e.status = com.team2.project.experience.domain.ExperienceStatus.COMPLETED "
		+ "group by e.legalCase.id")
	List<CaseParticipantCount> countCompletedByAttemptNoGroupByCase(@Param("attemptNo") int attemptNo);
}
