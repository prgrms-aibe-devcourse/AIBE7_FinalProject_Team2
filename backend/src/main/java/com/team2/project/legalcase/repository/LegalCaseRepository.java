package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CaseStatus;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.LegalCase;
import jakarta.persistence.LockModeType;
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
}
