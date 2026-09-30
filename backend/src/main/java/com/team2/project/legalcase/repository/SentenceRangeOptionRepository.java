package com.team2.project.legalcase.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.SentenceRangeOption;

public interface SentenceRangeOptionRepository extends JpaRepository<SentenceRangeOption, Long> {

	List<SentenceRangeOption> findByCrimeTypeOrderByDisplayOrder(CrimeType crimeType);

	boolean existsByIdAndCrimeType(Long id, CrimeType crimeType);
}
