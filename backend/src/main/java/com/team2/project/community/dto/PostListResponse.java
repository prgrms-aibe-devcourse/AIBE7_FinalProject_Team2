package com.team2.project.community.dto;

import java.util.List;

/**
 * C-1 글 목록. posts는 첫 글부터 page쪽 끝까지(쪽마다 6개씩 늘어난다 — 화면의 "더 보기"가 다시 부르는 방식, 목과 같다).
 *
 * @param popular 많이 본 글 셋 (좋아요 순)
 * @param cases   말머리 · 사건 고르기에 쓰는 공개 사건
 */
public record PostListResponse(List<PostView> posts, long total, boolean hasMore, List<PostLink> popular,
	List<CaseLink> cases) {

	public record PostLink(long postId, String title) {
	}

	public record CaseLink(long caseId, String title) {
	}
}
