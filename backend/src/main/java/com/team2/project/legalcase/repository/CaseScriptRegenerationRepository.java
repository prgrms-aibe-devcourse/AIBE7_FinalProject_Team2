package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CaseScriptRegeneration;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseScriptRegenerationRepository extends JpaRepository<CaseScriptRegeneration, Long> {

	/** 사건의 재생성 작업 (다른 사건의 작업 번호로 묻는 요청을 404로) */
	Optional<CaseScriptRegeneration> findByIdAndLegalCaseId(Long id, Long caseId);

	/** 사건의 가장 최근 재생성. 반영할 수 있는 것은 이것뿐이고, 새 요청 전에 도는 작업이 있는지도 이것으로 본다 */
	Optional<CaseScriptRegeneration> findFirstByLegalCaseIdOrderByIdDesc(Long caseId);

	/** 반영할 때 행을 잠그고 읽는다 — 버튼을 두 번 누르면 같은 결과를 두 번 반영해 판이 두 번 오른다 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from CaseScriptRegeneration r where r.id = :id")
	Optional<CaseScriptRegeneration> findByIdForUpdate(@Param("id") Long id);
}
