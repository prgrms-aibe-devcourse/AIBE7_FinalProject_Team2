package com.team2.project.community.repository;

import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.judgment.domain.Judgment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 커뮤니티가 읽는 판결 · 체험 상태 (BE-115). 판결을 확정한 뒤(판결 확정 · 실제 판결 공개 · 비교 공개)만 센다 */
public interface CommunityJudgmentReader extends Repository<Judgment, Long> {

	/** 붙일 수 있는 내 판결: 판결을 확정한 체험의 내 최종 판결, 최근 것부터. 공개 사건만 */
	@Query("""
		select j from Judgment j join fetch j.experience e join fetch j.legalCase c
		where e.anonymousUser.id = :anonymousUserId and e.status in :statuses
		  and j.subjectType = com.team2.project.judgment.domain.SubjectType.USER
		  and j.timing = com.team2.project.judgment.domain.Timing.FINAL
		  and c.status = com.team2.project.legalcase.domain.CaseStatus.PUBLISHED
		order by j.createdAt desc, j.id desc
		""")
	List<Judgment> findConfirmedFinals(@Param("anonymousUserId") UUID anonymousUserId,
		@Param("statuses") Collection<ExperienceStatus> statuses, Pageable pageable);

	@Query("""
		select j from Judgment j join fetch j.experience e join fetch j.legalCase c
		where e.anonymousUser.id = :anonymousUserId and e.status in :statuses and c.id = :caseId
		  and j.subjectType = com.team2.project.judgment.domain.SubjectType.USER
		  and j.timing = com.team2.project.judgment.domain.Timing.FINAL
		  and c.status = com.team2.project.legalcase.domain.CaseStatus.PUBLISHED
		order by j.createdAt desc, j.id desc
		""")
	List<Judgment> findConfirmedFinalsOfCase(@Param("anonymousUserId") UUID anonymousUserId,
		@Param("statuses") Collection<ExperienceStatus> statuses, @Param("caseId") long caseId, Pageable pageable);

	/** 판결을 확정한 사건 ID (보는 사람의 viewerJudged) */
	@Query("""
		select distinct e.legalCase.id from Experience e
		where e.anonymousUser.id = :anonymousUserId and e.status in :statuses
		""")
	List<Long> findJudgedCaseIds(@Param("anonymousUserId") UUID anonymousUserId,
		@Param("statuses") Collection<ExperienceStatus> statuses);
}
