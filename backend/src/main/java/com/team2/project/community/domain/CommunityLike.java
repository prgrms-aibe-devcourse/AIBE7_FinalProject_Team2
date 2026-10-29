package com.team2.project.community.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 커뮤니티 좋아요 (BE-115, V37). 회원 한 명이 글 하나에 하나. 넣고 빼는 것은 저장소의 네이티브 쿼리로 한다 */
@Entity
@Table(name = "community_like")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityLike {

	@EmbeddedId
	private Key id;

	private Instant createdAt;

	@Embeddable
	public record Key(Long postId, Long memberId) implements Serializable {
	}
}
