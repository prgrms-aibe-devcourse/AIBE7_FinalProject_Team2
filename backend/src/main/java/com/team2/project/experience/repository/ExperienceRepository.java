package com.team2.project.experience.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {

	/** 사용자의 가장 최근 회차 체험 (API 1-3 "내 체험" 규칙). */
	Optional<Experience> findFirstByAnonymousUserIdAndCaseIdOrderByAttemptNoDesc(UUID anonymousUserId, Long caseId);

	/**
	 * 상태를 from일 때만 to로 바꾸는 조건부 갱신. 바뀐 행 수를 돌려주고, 0이면 이미 다른 요청이 상태를 바꾼 것이다.
	 * 같은 행을 동시에 갱신하면 나중 요청은 앞 요청이 끝날 때까지 기다렸다가 조건을 다시 검사한다.
	 */
	@Modifying(clearAutomatically = true)
	@Query("""
			update Experience e
			set e.status = :to, e.lastReviewedStep = :lastReviewedStep, e.preJudgedAt = :now, e.updatedAt = :now
			where e.id = :id and e.status = :from
			""")
	int advanceToPreJudged(Long id, ExperienceStatus from, ExperienceStatus to, int lastReviewedStep,
			OffsetDateTime now);

	/** 사건별 참여자 수: attempt_no = 1이고 COMPLETED인 체험 수 (API 1 participantCount). */
	@Query("""
			select e.caseId as caseId, count(e) as participantCount
			from Experience e
			where e.attemptNo = 1 and e.status = com.team2.project.experience.domain.ExperienceStatus.COMPLETED
			group by e.caseId
			""")
	List<CaseParticipantCount> countCompletedFirstAttempts();
}
