package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CaseScript;
import com.team2.project.legalcase.domain.ScriptUnit;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseScriptRepository extends JpaRepository<CaseScript, Long> {

	Optional<CaseScript> findByLegalCaseId(Long caseId);

	/**
	 * 공개 대본이 있는 사건 ID (BE-116 목록의 scriptReady, 기준은 BE-126). 조건은 {@link CaseScript#isPublic()}과 같다 —
	 * 다섯 단위(U0 ~ U4)가 모두 승인됐다. 블록은 대본마다 단위 하나에 한 행이고(uk_case_script_block_unit) 단위는 다섯
	 * 값뿐이라(CHECK) "승인 블록 수 = 단위 수"가 곧 그 조건이다. 한쪽을 고치면 다른 쪽도 고쳐야 한다(CaseListApiTest ·
	 * CaseDetailApiTest가 API 18과 맞는지 본다).
	 */
	default Set<Long> findApprovedCaseIds() {
		return findCaseIdsWithApprovedBlocks(ScriptUnit.values().length);
	}

	@Query("""
		select s.legalCase.id from CaseScript s
		where (select count(b) from CaseScriptBlock b where b.script = s
		       and b.reviewStatus = com.team2.project.judgment.domain.ReviewStatus.APPROVED) = :unitCount
		""")
	Set<Long> findCaseIdsWithApprovedBlocks(@Param("unitCount") long unitCount);
}
