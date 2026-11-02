package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CasePaper;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CasePaperRepository extends JpaRepository<CasePaper, Long> {

	Optional<CasePaper> findByLegalCaseId(Long caseId);

	/** 공개 조건 NO_PAPER(BE-147). headline · article이 NOT NULL이라 행이 있으면 지면이 있는 것이다 */
	boolean existsByLegalCaseId(Long caseId);
}
