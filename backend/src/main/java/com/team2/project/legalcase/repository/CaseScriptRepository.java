package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CaseScript;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CaseScriptRepository extends JpaRepository<CaseScript, Long> {

	Optional<CaseScript> findByLegalCaseId(Long caseId);

	/**
	 * 모든 블록이 승인된 대본이 있는 사건 ID (BE-116 목록의 scriptReady, 기준은 BE-126). 조건은 {@link CaseScript#isPublic()}과
	 * 같다 — 블록이 하나 이상이고 승인되지 않은 블록이 없다. 한쪽을 고치면 다른 쪽도 고쳐야 한다(CaseListApiTest ·
	 * CaseDetailApiTest가 API 18과 맞는지 본다).
	 */
	@Query("""
		select s.legalCase.id from CaseScript s
		where exists (select b.id from CaseScriptBlock b where b.script = s)
		  and not exists (select b.id from CaseScriptBlock b where b.script = s
		                  and (b.reviewStatus is null
		                       or b.reviewStatus <> com.team2.project.judgment.domain.ReviewStatus.APPROVED))
		""")
	Set<Long> findApprovedCaseIds();
}
