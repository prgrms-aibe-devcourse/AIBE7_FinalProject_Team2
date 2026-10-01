package com.team2.project.comparison.repository;

import com.team2.project.comparison.domain.ComparisonAnalysis;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** (확장 단계) 세 판결 비교 분석 */
public interface ComparisonAnalysisRepository extends JpaRepository<ComparisonAnalysis, Long> {

	/** 체험의 비교 분석 (API 15) */
	@Query("select a from ComparisonAnalysis a where a.experience.id = :experienceId")
	Optional<ComparisonAnalysis> findByExperienceId(@Param("experienceId") Long experienceId);
}
