package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.RevealStage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FactorRepository extends JpaRepository<Factor, Long> {

	/** 사건의 판단 요소 전체, 표시 순서 (API 8 · 9 · 14) */
	@Query("select f from Factor f where f.legalCase.id = :caseId order by f.displayOrder")
	List<Factor> findAllByCaseId(@Param("caseId") Long caseId);

	/** 공개 단계로 걸러낸 판단 요소 (API 4 사전 판단 선택지는 OVERVIEW만) */
	@Query("select f from Factor f where f.legalCase.id = :caseId and f.revealStage = :stage order by f.displayOrder")
	List<Factor> findAllByCaseIdAndRevealStage(@Param("caseId") Long caseId, @Param("stage") RevealStage stage);

	/** 이 사건 · 이 공개 단계 요소 중 ids에 든 것의 개수 (API 5 검증). 남의 사건 · 다른 단계 요소는 세지 않는다 */
	@Query("select count(f) from Factor f where f.legalCase.id = :caseId and f.revealStage = :stage and f.id in :ids")
	long countByCaseIdAndRevealStageAndIdIn(@Param("caseId") Long caseId, @Param("stage") RevealStage stage,
		@Param("ids") Collection<Long> ids);
}
