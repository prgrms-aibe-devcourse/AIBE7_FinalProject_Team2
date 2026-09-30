package com.team2.project.legalcase.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.team2.project.legalcase.domain.CaseStatus;
import com.team2.project.legalcase.domain.LegalCase;

public interface LegalCaseRepository extends JpaRepository<LegalCase, Long> {

	/** 게시일 최신순. 게시일이 같거나 없으면 ID 큰 순으로 고정한다. */
	@Query("select c from LegalCase c where c.status = :status order by c.publishedAt desc nulls last, c.id desc")
	List<LegalCase> findByStatusLatestFirst(CaseStatus status);

	boolean existsByIdAndStatus(Long id, CaseStatus status);

	Optional<LegalCase> findByIdAndStatus(Long id, CaseStatus status);
}
