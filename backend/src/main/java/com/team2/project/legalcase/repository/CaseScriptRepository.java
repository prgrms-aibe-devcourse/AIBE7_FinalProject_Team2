package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CaseScript;
import com.team2.project.legalcase.domain.ScriptStatus;
import com.team2.project.legalcase.domain.ScriptUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseScriptRepository extends JpaRepository<CaseScript, Long> {

	Optional<CaseScript> findByLegalCaseId(Long caseId);

	/**
	 * 사건마다 사용자 쪽 대본 상태 (BE-126 2차-2, 목록 API 1 · 커뮤니티 메인의 {@code scriptStatus}). 사건마다 조회하지 않고 두 번에
	 * 읽는다. 준비 중(대본 없음 · 한 번도 나가지 않음)인 사건은 담지 않는다 — {@code getOrDefault(caseId, PREPARING)}으로 읽는다.
	 * 조건은 {@link CaseScript#status()}와 같다
	 */
	default Map<Long, ScriptStatus> findScriptStatuses() {
		Map<Long, ScriptStatus> statuses = new HashMap<>();
		findReleasedCaseIds().forEach(caseId -> statuses.put(caseId, ScriptStatus.MAINTENANCE));
		findApprovedCaseIds().forEach(caseId -> statuses.put(caseId, ScriptStatus.READY));
		return statuses;
	}

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

	/** 한 번 사용자에게 나간 대본이 있는 사건 ID ({@link CaseScript#getReleasedAt()}). 지금 공개 대본이 아니면 점검 중이다 */
	@Query("select s.legalCase.id from CaseScript s where s.releasedAt is not null")
	Set<Long> findReleasedCaseIds();
}
