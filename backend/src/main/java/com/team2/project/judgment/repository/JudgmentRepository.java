package com.team2.project.judgment.repository;

import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.SubjectType;
import java.util.List;
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

	/** 사건의 공개된 AI 또는 재판부 판결 1건 (API 10 · 11, 부분 유니크 uk_judgment_published) */
	@Query("select j from Judgment j where j.legalCase.id = :caseId and j.subjectType = :subjectType "
		+ "and j.timing = com.team2.project.judgment.domain.Timing.FINAL and j.isPublished = true")
	Optional<Judgment> findPublishedJudgment(@Param("caseId") Long caseId,
		@Param("subjectType") SubjectType subjectType);

	/**
	 * 사건의 공개된 AI · 재판부 판결을 한 번에 (API 12 · 14)
	 * 주체별로 각각 조회하면 같은 조건의 쿼리가 반복되므로, 둘 다 필요한 곳은 이 메서드를 쓴다.
	 * 부분 유니크 제약(uk_judgment_published) 덕분에 주체별로 1건씩, 최대 2건이다.
	 */
	@Query("select j from Judgment j where j.legalCase.id = :caseId "
		+ "and j.subjectType in (com.team2.project.judgment.domain.SubjectType.AI, "
		+ "com.team2.project.judgment.domain.SubjectType.COURT) "
		+ "and j.timing = com.team2.project.judgment.domain.Timing.FINAL and j.isPublished = true")
	List<Judgment> findPublishedJudgments(@Param("caseId") Long caseId);
}
