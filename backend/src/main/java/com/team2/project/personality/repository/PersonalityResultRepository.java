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

	/** 성향 테스트를 한 적이 있는지 (도전과제 hasType, BE-96) */
	boolean existsByAnonymousUser_IdAndCurrentTrue(UUID anonymousUserId);

	/** 응시 기록이 하나라도 있는지 — 재심은 첫 테스트가 있어야 할 수 있다 (P-2 v0.50, BE-104) */
	boolean existsByAnonymousUser_Id(UUID anonymousUserId);

	/** 성향카드 재심을 이미 했는지 (사용자당 1건, BE-104) */
	boolean existsByAnonymousUser_IdAndBaselineRetakeTrue(UUID anonymousUserId);

	/** 처음 성향(초심) = 가장 오래된 응시 행. 재응시 · 재심으로 바뀌지 않는다 (P-5 v0.50, BE-104) */
	Optional<PersonalityResult> findFirstByAnonymousUser_IdOrderByCreatedAtAscIdAsc(UUID anonymousUserId);

	/** 성향카드 재심 결과 (없으면 아직 안 함, BE-104) */
	Optional<PersonalityResult> findByAnonymousUser_IdAndBaselineRetakeTrue(UUID anonymousUserId);

	/**
	 * 재응시 전에 이전 현재 결과를 내린다(이력은 남긴다). 새 결과 저장과 같은 트랜잭션에서 먼저 부른다 —
	 * 부분 유니크(사용자마다 현재 결과 1건)는 문장마다 검사하므로 순서가 바뀌면 위반이 난다.
	 * <p>서비스(7.6.4)에서 챙길 것 (#124 리뷰)
	 * <ul>
	 *   <li>동시 재응시: 두 요청이 모두 clearCurrent 후 INSERT하면 부분 유니크 위반이 난다. 사용자 행 비관적 락 등으로 직렬화한다</li>
	 *   <li>clearAutomatically로 영속성 컨텍스트가 비워진다. 앞서 조회한 AnonymousUser · PersonalityType은 detach되므로
	 *       이 호출 뒤에 다시 조회하거나 getReferenceById로 잡는다</li>
	 * </ul>
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE PersonalityResult r SET r.current = false WHERE r.anonymousUser.id = :anonymousUserId AND r.current = true")
	int clearCurrent(@Param("anonymousUserId") UUID anonymousUserId);
}
