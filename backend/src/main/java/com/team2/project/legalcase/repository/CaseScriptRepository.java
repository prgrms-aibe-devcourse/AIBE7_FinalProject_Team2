package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CaseScript;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseScriptRepository extends JpaRepository<CaseScript, Long> {

	Optional<CaseScript> findByLegalCaseId(Long caseId);
}
