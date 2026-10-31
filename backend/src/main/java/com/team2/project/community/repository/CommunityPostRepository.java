package com.team2.project.community.repository;

import com.team2.project.community.domain.CommunityPost;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long>,
	JpaSpecificationExecutor<CommunityPost> {

	/** 많이 본 글: 조회 수를 세지 않으므로 좋아요 순으로 고른다(같으면 최신) */
	List<CommunityPost> findTop3ByOrderByLikeCountDescCreatedAtDescIdDesc();

	/** 사건 글 몇 개: 좋아요 순(같으면 최신) */
	List<CommunityPost> findByCaseIdOrderByLikeCountDescCreatedAtDescIdDesc(Long caseId, Pageable pageable);

	/** 좋아요 수를 더하거나 뺀다. 읽고 쓰지 않고 한 문장으로 바꿔 동시에 눌러도 수가 어긋나지 않는다 */
	@Modifying
	@Query("update CommunityPost p set p.likeCount = p.likeCount + :delta where p.id = :id")
	int addLikeCount(@Param("id") long id, @Param("delta") int delta);

	/** 좋아요 수를 DB에서 다시 읽는다(원자적 UPDATE 뒤라 영속성 컨텍스트의 값은 옛 값이다) */
	@Query("select p.likeCount from CommunityPost p where p.id = :id")
	int findLikeCount(@Param("id") long id);

	@Modifying
	@Query("update CommunityPost p set p.commentCount = p.commentCount + 1 where p.id = :id")
	int incrementCommentCount(@Param("id") long id);

	/** 사건별 이야기 수: since 이후의 글 + 그 사건 글에 달린 댓글 */
	@Query(value = """
		select t.case_id as caseId, count(*) as count from (
		    select case_id from community_post where case_id is not null and created_at >= :since
		    union all
		    select p.case_id from community_comment c join community_post p on p.id = c.post_id
		    where p.case_id is not null and c.created_at >= :since
		) t group by t.case_id
		""", nativeQuery = true)
	List<CaseCount> countTalkByCaseSince(@Param("since") Instant since);

	/** 사건별 글 수 (전체 기간) */
	@Query(value = "select case_id as caseId, count(*) as count from community_post where case_id in (:caseIds) group by case_id",
		nativeQuery = true)
	List<CaseCount> countPostsByCase(@Param("caseIds") Collection<Long> caseIds);

	/**
	 * 랭킹 후보: 글이나 댓글을 쓴 회원. 회원 수만큼 명함을 계산하므로 회원이 많아지면 미리 계산해 두는 쪽으로 바꾼다(docs/decisions/BE-115.md)
	 */
	@Query(value = """
		select member_id from community_post where member_id is not null
		union
		select member_id from community_comment where member_id is not null
		""", nativeQuery = true)
	List<Long> findWriterIds();

	interface CaseCount {
		Long getCaseId();

		long getCount();
	}
}
