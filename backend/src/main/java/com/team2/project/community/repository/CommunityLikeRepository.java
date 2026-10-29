package com.team2.project.community.repository;

import com.team2.project.community.domain.CommunityLike;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityLikeRepository extends JpaRepository<CommunityLike, CommunityLike.Key> {

	/**
	 * 좋아요를 넣는다. 이미 있으면(동시에 두 번 누름) 아무것도 하지 않는다 — 기본 키 충돌을 예외로 받으면
	 * 트랜잭션이 롤백 전용이 되므로 ON CONFLICT DO NOTHING (XpEventRepository와 같다)
	 * @return 새로 넣은 행 수
	 */
	@Modifying
	@Query(value = """
		insert into community_like (post_id, member_id) values (:postId, :memberId)
		on conflict (post_id, member_id) do nothing
		""", nativeQuery = true)
	int insertIfAbsent(@Param("postId") long postId, @Param("memberId") long memberId);

	@Modifying
	@Query(value = "delete from community_like where post_id = :postId and member_id = :memberId", nativeQuery = true)
	int deleteOne(@Param("postId") long postId, @Param("memberId") long memberId);

	/** 이 회원이 좋아요를 누른 글 ID (보여 줄 글 중에서) */
	@Query(value = "select post_id from community_like where member_id = :memberId and post_id in (:postIds)",
		nativeQuery = true)
	List<Long> findLikedPostIds(@Param("memberId") long memberId, @Param("postIds") Collection<Long> postIds);
}
