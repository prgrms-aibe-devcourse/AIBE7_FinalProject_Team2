package com.team2.project.community.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 커뮤니티 글 (BE-115, V37). 글쓴이는 회원(memberId)이고, 명함 · 경험치는 쓴 요청의 익명 ID(anonymousUserId) 기준이다.
 * 좋아요 수 · 댓글 수는 목록 정렬 · 쪽 나누기용으로 함께 센다 — 바꾸는 것은 저장소의 원자적 UPDATE만 한다
 */
@Entity
@Table(name = "community_post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPost {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long memberId;
	private UUID anonymousUserId;

	@Enumerated(EnumType.STRING)
	private CommunityCategory category;

	private Long caseId;
	private String title;
	private String body;

	@JdbcTypeCode(SqlTypes.JSON)
	private SharedVerdict verdict;

	private int likeCount;
	private int commentCount;
	private Instant createdAt;

	public static CommunityPost of(long memberId, UUID anonymousUserId, CommunityCategory category, Long caseId,
		String title, String body, SharedVerdict verdict, Instant now) {
		CommunityPost post = new CommunityPost();
		post.memberId = memberId;
		post.anonymousUserId = anonymousUserId;
		post.category = category;
		post.caseId = caseId;
		post.title = title;
		post.body = body;
		post.verdict = verdict;
		post.createdAt = now;
		return post;
	}
}
