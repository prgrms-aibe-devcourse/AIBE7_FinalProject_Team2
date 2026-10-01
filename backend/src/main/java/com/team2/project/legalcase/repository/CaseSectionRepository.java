package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CaseSection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseSectionRepository extends JpaRepository<CaseSection, Long> {

	/**
	 * 사건의 섹션 전체 (API 6). display_order는 단계(stage) 안에서의 순서라 단계별로 묶어 정렬한다.
	 * 화면 단계 순서(DETAIL → ARGUMENT → LAW → SUMMARY)로 묶는 것과 열린 섹션만 응답하는 필터는 서비스에서 한다.
	 */
	@Query("select s from CaseSection s where s.legalCase.id = :caseId order by s.stage, s.displayOrder")
	List<CaseSection> findAllByCaseId(@Param("caseId") Long caseId);
}
