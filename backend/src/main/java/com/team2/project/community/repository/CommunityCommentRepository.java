package com.team2.project.community.repository;

import com.team2.project.community.domain.CommunityComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {

	/** 글의 댓글, 오래된 순 */
	List<CommunityComment> findByPostIdOrderByIdAsc(Long postId);
}
