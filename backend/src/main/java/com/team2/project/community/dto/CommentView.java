package com.team2.project.community.dto;

import java.time.OffsetDateTime;

/** 댓글 (API 명세 12-1). mine: 보는 사람이 쓴 댓글인가 */
public record CommentView(long commentId, AuthorCard author, String body, OffsetDateTime createdAt, boolean mine) {
}
