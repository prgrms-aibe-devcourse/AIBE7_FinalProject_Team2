package com.team2.project.judgment.repository;

import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.SubjectType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 판단 조회는 목적별 메서드로만 한다. timing · subject_type 조건을 서비스 코드에 쓰지 않는다 (ERD 7장)
 */
public interface JudgmentRepository extends JpaRepository<Judgment, Long> {

	/** 관리자 검수(A-6): 사건의 AI · 재판부 판결 후보 전부, 최신 순 (반려 이력 포함) */
	@Query("select j from Judgment j where j.legalCase.id = :caseId "
		+ "and j.subjectType in (com.team2.project.judgment.domain.SubjectType.AI, com.team2.project.judgment.domain.SubjectType.COURT) "
		+ "order by j.createdAt desc, j.id desc")
	List<Judgment> findCandidatesByCaseId(@Param("caseId") Long caseId);

	/** 관리자 검수(A-9 · A-10): 후보의 사건 id. 사건 행을 먼저 잠근 뒤 후보를 읽기 위해 따로 읽는다(사용자 판결은 없음) */
	@Query("select j.legalCase.id from Judgment j where j.id = :id "
		+ "and j.subjectType <> com.team2.project.judgment.domain.SubjectType.USER")
	Optional<Long> findCandidateCaseId(@Param("id") Long id);

	/**
	 * 승인(A-9) 1단계: 같은 사건 · 같은 주체의 다른 공개 판결을 내린다(검수 상태는 그대로).
	 * 검수 기록 없이 공개돼 있던 판결(시드 등)은 내린 뒤 "대기"로 보이지 않게 승인으로 굳혀 둔다(reviewState 규칙)
	 * 부분 유니크 인덱스(uk_judgment_published)는 문장마다 검사하므로, 이 UPDATE가 승인보다 먼저 DB에 반영돼야 한다
	 */
	@Modifying(flushAutomatically = true)
	@Query("update Judgment j set j.isPublished = false, "
		+ "j.reviewStatus = coalesce(j.reviewStatus, com.team2.project.judgment.domain.ReviewStatus.APPROVED) "
		+ "where j.legalCase.id = :caseId and j.subjectType = :subjectType "
		+ "and j.isPublished = true and j.id <> :keepId")
	int unpublishOthers(@Param("caseId") Long caseId, @Param("subjectType") SubjectType subjectType,
		@Param("keepId") Long keepId);

	/** 내 사전 판단 (API 3은 선택 구간, API 14는 선택 요소도 응답) */
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
