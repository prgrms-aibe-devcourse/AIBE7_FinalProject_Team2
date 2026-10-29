package com.team2.project.community.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.team2.project.community.dto.AuthorCard.CardLine;
import com.team2.project.community.dto.AuthorCard.ShownType;
import com.team2.project.community.dto.PostListResponse.CaseLink;
import com.team2.project.community.dto.PostListResponse.PostLink;
import com.team2.project.level.dto.LevelStatus.JobView;
import com.team2.project.profile.domain.ProfileDecoValues;
import java.util.List;

/** C-6 커뮤니티 메인 (FE-89): 지금 이야기가 많은 사건 · 추천 사건 · 레벨 랭킹 + 많이 본 글 · 공개 사건 */
public record CommunityHomeResponse(List<CaseBlock> hotCases, List<CaseBlock> recommendedCases,
	List<RankingItem> ranking, List<PostLink> popular, List<CaseLink> cases) {

	/**
	 * @param talkCount 최근 하루 이야기 수(글 + 댓글). 이야기 많은 사건에만 있고 추천 사건에는 없다
	 * @param postCount 이 사건 글 수 (전체 기간)
	 * @param posts     이 사건 글 몇 개 (좋아요 순, 이야기 많은 사건 3개 · 추천 사건 2개)
	 */
	public record CaseBlock(@JsonProperty("case") CaseSummary caseSummary,
		@JsonInclude(JsonInclude.Include.NON_NULL) Long talkCount, long postCount, List<CasePost> posts) {
	}

	/** 메인에 싣는 사건 요약: 신문 지면(API 17)의 제목 · 부제 · 본문 두 단락과 사건 목록(API 1)의 값. 지면이 없으면 headline · deck은 null, article은 빈 배열 */
	public record CaseSummary(long caseId, String title, String crimeCategoryLabel, String shortIntro, String headline,
		String deck, List<String> article, String thumbnailUrl, long participantCount, Integer estimatedMinutes,
		boolean scriptReady) {
	}

	public record CasePost(long postId, String title, int likeCount, int commentCount, AuthorCard author) {
	}

	/** 레벨 순(같으면 체험한 사건 수 → 닉네임 순). 글쓴이 명함에서 한 줄 소개만 뺀 값 */
	public record RankingItem(int rank, String nickname, int level, JobView job, CardLine cardLine, int completedCount,
		ProfileDecoValues profileCard, ShownType shownType) {
	}
}
