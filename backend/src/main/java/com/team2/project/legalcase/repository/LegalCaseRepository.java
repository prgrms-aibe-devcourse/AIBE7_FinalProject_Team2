package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CaseStatus;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.LegalCase;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LegalCaseRepository extends JpaRepository<LegalCase, Long> {

	/**
	 * 공개 사건 목록, 최신 공개순 (API 1)
	 * PostgreSQL은 DESC 정렬에서 NULL을 맨 앞에 두므로, 게시일이 없는 사건은 뒤로 보내고 ID 큰 순으로 고정한다.
	 */
	@Query("select c from LegalCase c where c.status = com.team2.project.legalcase.domain.CaseStatus.PUBLISHED "
		+ "order by c.publishedAt desc nulls last, c.id desc")
	List<LegalCase> findAllPublished();

	/** 범죄 유형별 공개 사건 목록 (API 1 crimeType 필터) */
	@Query("select c from LegalCase c where c.status = com.team2.project.legalcase.domain.CaseStatus.PUBLISHED "
		+ "and c.crimeType = :crimeType order by c.publishedAt desc nulls last, c.id desc")
	List<LegalCase> findAllPublishedByCrimeType(@Param("crimeType") CrimeType crimeType);

	/** 주어진 id 중 공개 중인 사건의 id (수집 화이트리스트 BE-136 — 일러스트가 가리키는 사건 확인) */
	@Query("select c.id from LegalCase c where c.status = com.team2.project.legalcase.domain.CaseStatus.PUBLISHED "
		+ "and c.id in :ids")
	List<Long> findPublishedIdsIn(@Param("ids") Collection<Long> ids);

	/** 공개 사건 1건 (비공개 사건은 없는 것으로 본다, CASE_NOT_FOUND) */
	default Optional<LegalCase> findPublishedById(Long caseId) {
		return findByIdAndStatus(caseId, CaseStatus.PUBLISHED);
	}

	Optional<LegalCase> findByIdAndStatus(Long id, CaseStatus status);

	/** 관리자 사건 수정(A-11): 다른 사건이 이미 쓰는 제목인지 (uk_legal_case_title) */
	boolean existsByTitleAndIdNot(String title, Long id);

	/** 사건 등록 적재(BE-93 2차): 같은 제목이 이미 있는지. uk_legal_case_title에 걸리기 전에 막는다 */
	boolean existsByTitle(String title);

	/**
	 * 관리자 상태 변경(A-9 ~ A-13)은 사건 행을 먼저 잠그고(SELECT ... FOR UPDATE) 상태를 확인한다 (API 명세 8-2 "동시 요청").
	 * "아직 공개 전"을 확인한 사이 다른 관리자가 공개 · 승인하는 경합을 막는다
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from LegalCase c where c.id = :id")
	Optional<LegalCase> findByIdForUpdate(@Param("id") Long id);

	/** 공개 사건인지만 확인 (엔티티를 읽지 않는다) */
	default boolean existsPublishedById(Long caseId) {
		return existsByIdAndStatus(caseId, CaseStatus.PUBLISHED);
	}

	boolean existsByIdAndStatus(Long id, CaseStatus status);

	/**
	 * 배역 배정(BE-125)을 범죄 유형별로 한 번에 하나씩만 하게 잠근다. 트랜잭션이 끝나면 풀린다.
	 *
	 * <p>행 잠금(FOR UPDATE)으로는 막을 수 없다 — 같은 유형의 두 사건이 동시에 적재되면 둘 다 "마지막 배정"으로
	 * 같은 기존 행을 읽는데, 막아야 할 것은 아직 없는 <b>새 행</b>이기 때문이다. 권고 잠금(advisory lock)은
	 * 앞 트랜잭션이 커밋할 때까지 기다리게 하고, 그 뒤의 조회는 새 행까지 본다(READ COMMITTED).
	 * pg_advisory_xact_lock은 void를 돌려주므로 바깥 SELECT로 감싸 정수를 받는다.
	 */
	@Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('legal_case_cast:' || :crimeType))) AS locked",
		nativeQuery = true)
	Integer lockCastAssignment(@Param("crimeType") String crimeType);

	/**
	 * 범죄 유형별 피고인 역(defense) 캐릭터마다 배정된 수 (BE-125). 배정하지 않은 사건은 세지 않는다.
	 *
	 * <p>(BE-129) 배역은 등록 요청을 받을 때 정하므로 <b>아직 적재되지 않은 등록</b>(case_import, case_id 없음)도 센다 —
	 * 빼면 등록 중인 사건이 있을 때 다음 등록이 같은 배역을 받는다. 실패한 등록(FAILED)은 사건이 되지 않으므로 빼고,
	 * 적재된 등록은 사건 쪽에서 세므로 뺀다. 생성은 끝났지만 끝내 적재하지 않은 등록(SUCCEEDED)도 세는데, 드물고 기간으로
	 * 자르면 기준이 하나 더 생겨 그대로 둔다(docs/decisions/BE-129.md)
	 */
	@Query(value = """
		SELECT defense, count(*) AS cnt FROM (
		    SELECT cast_roles->>'defense' AS defense FROM legal_case
		    WHERE crime_type = :crimeType AND cast_roles IS NOT NULL
		    UNION ALL
		    SELECT cast_roles->>'defense' FROM case_import
		    WHERE crime_type = :crimeType AND cast_roles IS NOT NULL AND case_id IS NULL AND status <> 'FAILED'
		) assigned GROUP BY defense""",
		nativeQuery = true)
	List<Object[]> countDefenseByCrimeType(@Param("crimeType") String crimeType);

	/**
	 * 범죄 유형별로 가장 나중에 배정된 피고인 역. 없으면 null (BE-125). 위와 같은 대상에서 만든 시각이 가장 늦은 것이다(BE-129) —
	 * 사건과 등록은 id가 다른 순번이라 시각으로 맞춘다. 같은 시각이면 등록 쪽(더 나중에 배정한 쪽), 그다음 id가 큰 쪽을 앞에 둔다(한 트랜잭션에서 만든 행은 시각이 같다).
	 *
	 * <p>시각은 <b>배역을 정한 때</b>다. 등록 때 정한 배역이 그대로 사건 배역이 됐으면 등록 시각을 쓴다 — 적재 시각을 쓰면
	 * 등록 순서와 다르게 적재했을 때 "마지막"이 바뀐다. 등록 때 배역을 정하지 않았거나(V46 전 등록 · 대본을 끈 서버)
	 * 대본 배역이 달라 적재 때 정해졌으면 사건(적재) 시각을 쓴다
	 */
	@Query(value = """
		SELECT defense FROM (
		    SELECT lc.cast_roles->>'defense' AS defense,
		           CASE WHEN ci.cast_roles IS NOT NULL AND lc.cast_roles = ci.cast_roles
		                THEN ci.created_at ELSE lc.created_at END AS created_at,
		           0 AS pending, lc.id
		    FROM legal_case lc
		    LEFT JOIN case_import ci ON ci.case_id = lc.id
		    WHERE lc.crime_type = :crimeType AND lc.cast_roles IS NOT NULL
		    UNION ALL
		    SELECT cast_roles->>'defense', created_at, 1, id FROM case_import
		    WHERE crime_type = :crimeType AND cast_roles IS NOT NULL AND case_id IS NULL AND status <> 'FAILED'
		) assigned ORDER BY created_at DESC, pending DESC, id DESC LIMIT 1""",
		nativeQuery = true)
	String findLastDefenseByCrimeType(@Param("crimeType") String crimeType);
}
