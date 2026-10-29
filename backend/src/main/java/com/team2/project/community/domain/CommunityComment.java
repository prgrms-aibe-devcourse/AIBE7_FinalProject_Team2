package com.team2.project.community.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 커뮤니티 댓글 (BE-115, V38). 글쓴이 · 명함 기준은 글과 같다 */
@Entity
@Table(name = "community_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityComment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long postId;
	private Long memberId;
	private UUID anonymousUserId;
	private String body;
	private Instant createdAt;

	public static CommunityComment of(long postId, long memberId, UUID anonymousUserId, String body, Instant now) {
		CommunityComment comment = new CommunityComment();
		comment.postId = postId;
		comment.memberId = memberId;
		comment.anonymousUserId = anonymousUserId;
		comment.body = body;
		comment.createdAt = now;
		return comment;
	}
}
