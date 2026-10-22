package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CaseSource;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseSourceRepository extends JpaRepository<CaseSource, Long> {

	/** 관리자 검수(A-6): 사건의 원본 판결문 정보 전부 (원문은 A-7에서 따로) */
	@Query("select s from CaseSource s where s.legalCase.id = :caseId order by s.id")
	List<CaseSource> findAllByCaseId(@Param("caseId") Long caseId);

	/** 최종 확정 판결 1건 (API 12 출처 기관, 확장). 사건번호 등은 응답에 쓰지 않는다 */
	@Query("select s from CaseSource s where s.legalCase.id = :caseId and s.isFinal = true")
	Optional<CaseSource> findFinalByCaseId(@Param("caseId") Long caseId);

	/** 사건의 원본 판결문 사건번호 전부 (1심 · 항소심). RAG 자기 사건 제외 해시에만 쓴다 (BE-51, 응답에 넣지 않는다) */
	@Query("select s.caseNumber from CaseSource s where s.legalCase.id = :caseId")
	List<String> findCaseNumbersByCaseId(@Param("caseId") Long caseId);

	/** 이미 등록한 사건번호인지 (BE-93 A-14 DUPLICATE_SOURCE). 겹치는 사건번호만 돌려준다 */
	@Query("select distinct s.caseNumber from CaseSource s where s.caseNumber in :caseNumbers")
	List<String> findExistingCaseNumbers(@Param("caseNumbers") Collection<String> caseNumbers);
}
