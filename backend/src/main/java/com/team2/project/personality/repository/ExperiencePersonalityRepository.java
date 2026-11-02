package com.team2.project.personality.repository;

import com.team2.project.common.owner.Owner;
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

	/**
	 * 종합 성향 계산용: 사용자의 첫 회차 체험 기여만 (attempt_no = 1, 성향 산식 3-1). 완료 순서로 정렬한다.
	 * <p>기여 저장 시각(p.createdAt)이 아니라 완료 시각(e.completedAt)을 쓴다. 비교 공개(API 13)가 동시에 처리되면
	 * 먼저 완료된 요청의 기여 저장이 늦어 저장 순서가 완료 순서와 달라질 수 있기 때문이다(BE-99, #238 리뷰).
	 * 완료 시각이 없는 기여는 HistoryItem.of와 같이 기여 저장 시각으로 대신한다(null이 맨 뒤로 가 "최근"으로 잡히지 않게)
	 */
	@Query("SELECT p FROM ExperiencePersonality p JOIN FETCH p.experience e JOIN FETCH e.legalCase "
		+ "WHERE e.anonymousUser.id = :anonymousUserId AND e.attemptNo = 1 "
		+ "ORDER BY COALESCE(e.completedAt, p.createdAt), p.id")
	List<ExperiencePersonality> findFirstAttemptsByAnonymousUser(@Param("anonymousUserId") UUID anonymousUserId);

	/** findFirstAttemptsByAnonymousUser의 회원 쪽 (BE-140) */
	@Query("SELECT p FROM ExperiencePersonality p JOIN FETCH p.experience e JOIN FETCH e.legalCase "
		+ "WHERE e.memberId = :memberId AND e.attemptNo = 1 "
		+ "ORDER BY COALESCE(e.completedAt, p.createdAt), p.id")
	List<ExperiencePersonality> findFirstAttemptsByMember(@Param("memberId") long memberId);

	default List<ExperiencePersonality> findFirstAttempts(Owner owner) {
		return owner.fold(this::findFirstAttemptsByMember, this::findFirstAttemptsByAnonymousUser);
	}
}
