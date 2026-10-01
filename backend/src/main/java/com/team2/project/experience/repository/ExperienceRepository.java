package com.team2.project.experience.repository;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

	/** 체험의 현재 상태만 다시 읽는다 (조건부 갱신이 실패했을 때 currentStatus 응답용) */
	@Query("select e.status from Experience e where e.id = :id")
	Optional<ExperienceStatus> findStatusById(@Param("id") Long id);

	/**
	 * 체험의 현재 상태 + 마지막 확인 섹션을 함께 다시 읽는다.
	 * 조건부 갱신이 0건일 때, 섹션 확인(API 7)처럼 상태만으로는 "이미 그 지점 이상"을 판정할 수 없는 경우에 쓴다.
	 */
	@Query("select new com.team2.project.experience.repository.ExperienceState(e.status, e.lastReviewedStep) "
		+ "from Experience e where e.id = :id")
	Optional<ExperienceState> findStateById(@Param("id") Long id);

	/**
	 * findStateById와 같은 내용이지만 별도 트랜잭션에서 읽는다.
	 * 유니크 제약 위반 뒤에 쓴다 — PostgreSQL은 제약 위반이 나면 그 트랜잭션 전체를 실패 상태로 만들어
	 * 같은 트랜잭션에서는 SELECT조차 거절하므로, 새 트랜잭션(별도 커넥션)에서 읽어야 한다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@Query("select new com.team2.project.experience.repository.ExperienceState(e.status, e.lastReviewedStep) "
		+ "from Experience e where e.id = :id")
	Optional<ExperienceState> findStateInNewTransaction(@Param("id") Long id);

	/**
	 * 조건부 갱신: 읽은 뒤 다른 요청이 상태를 바꾸지 않았을 때만(status · lastReviewedStep이 그대로일 때만) 새 상태를 반영한다.
	 * 동시에 두 요청이 오면 하나만 1을 돌려받고 나머지는 0 (API 명세 API 5 · 9 "조건부 갱신")
	 * ExperienceTransitionService가 체험 엔티티를 영속성 컨텍스트에서 분리한 뒤 부르므로 컨텍스트를 비우지 않는다.
	 * flushAutomatically: 같은 트랜잭션에서 먼저 저장한 판단(Judgment 등)을 이 UPDATE 전에 반영한다.
	 */
	@Modifying(flushAutomatically = true)
	@Query("""
		update Experience e set
			e.status = :status,
			e.lastReviewedStep = :lastReviewedStep,
			e.preJudgedAt = :preJudgedAt,
			e.reviewedAt = :reviewedAt,
			e.verdictConfirmedAt = :verdictConfirmedAt,
			e.aiRevealedAt = :aiRevealedAt,
			e.completedAt = :completedAt,
			e.updatedAt = :updatedAt
		where e.id = :id and e.status = :expectedStatus and e.lastReviewedStep = :expectedStep""")
	int updateStateIfUnchanged(
		@Param("id") Long id,
		@Param("expectedStatus") ExperienceStatus expectedStatus,
		@Param("expectedStep") int expectedStep,
		@Param("status") ExperienceStatus status,
		@Param("lastReviewedStep") int lastReviewedStep,
		@Param("preJudgedAt") Instant preJudgedAt,
		@Param("reviewedAt") Instant reviewedAt,
		@Param("verdictConfirmedAt") Instant verdictConfirmedAt,
		@Param("aiRevealedAt") Instant aiRevealedAt,
		@Param("completedAt") Instant completedAt,
		@Param("updatedAt") Instant updatedAt);
}
