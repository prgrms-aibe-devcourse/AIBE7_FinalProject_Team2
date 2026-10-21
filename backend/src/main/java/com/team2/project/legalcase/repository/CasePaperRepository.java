package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CasePaper;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CasePaperRepository extends JpaRepository<CasePaper, Long> {

	Optional<CasePaper> findByLegalCaseId(Long caseId);
}
