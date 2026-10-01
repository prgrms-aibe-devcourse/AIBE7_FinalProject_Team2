package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CaseStatus;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.LegalCase;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LegalCaseRepository extends JpaRepository<LegalCase, Long> {

	/** 공개 사건 목록, 최신 공개순 (API 1) */
	@Query("select c from LegalCase c where c.status = com.team2.project.legalcase.domain.CaseStatus.PUBLISHED "
		+ "order by c.publishedAt desc, c.id desc")
	List<LegalCase> findAllPublished();

	/** 범죄 유형별 공개 사건 목록 (API 1 crimeType 필터) */
	@Query("select c from LegalCase c where c.status = com.team2.project.legalcase.domain.CaseStatus.PUBLISHED "
		+ "and c.crimeType = :crimeType order by c.publishedAt desc, c.id desc")
	List<LegalCase> findAllPublishedByCrimeType(@Param("crimeType") CrimeType crimeType);

	/** 공개 사건 1건 (비공개 사건은 없는 것으로 본다, CASE_NOT_FOUND) */
	default Optional<LegalCase> findPublishedById(Long caseId) {
		return findByIdAndStatus(caseId, CaseStatus.PUBLISHED);
	}

	Optional<LegalCase> findByIdAndStatus(Long id, CaseStatus status);
}
