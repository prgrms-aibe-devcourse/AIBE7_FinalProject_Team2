package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CaseSource;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseSourceRepository extends JpaRepository<CaseSource, Long> {

	/** 최종 확정 판결 1건 (API 12 출처 기관, 확장). 사건번호 등은 응답에 쓰지 않는다 */
	@Query("select s from CaseSource s where s.legalCase.id = :caseId and s.isFinal = true")
	Optional<CaseSource> findFinalByCaseId(@Param("caseId") Long caseId);
}
