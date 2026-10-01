package com.team2.project.judgment.repository;

import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.SubjectType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 판단 조회는 목적별 메서드로만 한다. timing · subject_type 조건을 서비스 코드에 쓰지 않는다 (ERD 7장)
 */
public interface JudgmentRepository extends JpaRepository<Judgment, Long> {

	/** 내 사전 판단 (API 14에서 처음 응답) */
	@Query("select j from Judgment j where j.experience.id = :experienceId "
		+ "and j.subjectType = com.team2.project.judgment.domain.SubjectType.USER "
		+ "and j.timing = com.team2.project.judgment.domain.Timing.PRE")
	Optional<Judgment> findPreJudgment(@Param("experienceId") Long experienceId);

	/** 내 최종 판결 (API 10 · 12 · 14) */
	@Query("select j from Judgment j where j.experience.id = :experienceId "
		+ "and j.subjectType = com.team2.project.judgment.domain.SubjectType.USER "
		+ "and j.timing = com.team2.project.judgment.domain.Timing.FINAL")
	Optional<Judgment> findUserFinalJudgment(@Param("experienceId") Long experienceId);

	/** 사건의 공개된 AI 또는 재판부 판결 1건 (API 10 · 12 · 14, 부분 유니크 uk_judgment_published) */
	@Query("select j from Judgment j where j.legalCase.id = :caseId and j.subjectType = :subjectType "
		+ "and j.timing = com.team2.project.judgment.domain.Timing.FINAL and j.isPublished = true")
	Optional<Judgment> findPublishedJudgment(@Param("caseId") Long caseId,
		@Param("subjectType") SubjectType subjectType);
}
