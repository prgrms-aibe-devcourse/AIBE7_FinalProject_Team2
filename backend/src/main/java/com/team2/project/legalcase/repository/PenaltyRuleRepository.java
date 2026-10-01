package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.PenaltyRule;
import com.team2.project.legalcase.domain.PenaltyType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PenaltyRuleRepository extends JpaRepository<PenaltyRule, Long> {

	/** 사건에서 고를 수 있는 형벌 규칙, 표시 순서 (API 6 · 8) */
	@Query("select r from PenaltyRule r where r.legalCase.id = :caseId order by r.displayOrder")
	List<PenaltyRule> findAllByCaseId(@Param("caseId") Long caseId);

	/** 고른 형벌의 규칙 1건 (API 9 선고 가능 범위 검증). 유니크 (case_id, penalty_type) */
	@Query("select r from PenaltyRule r where r.legalCase.id = :caseId and r.penaltyType = :penaltyType")
	Optional<PenaltyRule> findByCaseIdAndPenaltyType(@Param("caseId") Long caseId,
		@Param("penaltyType") PenaltyType penaltyType);
}
