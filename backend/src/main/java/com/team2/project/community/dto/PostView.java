package com.team2.project.community.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team2.project.community.domain.CommunityCategory;
import com.team2.project.community.domain.SharedVerdict;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 글 (API 명세 12-1). 목록 · 상세 · 작성 응답이 같은 모양이고, 상세만 comments가 있다.
 *
 * @param images       사진 주소. 사진 올리기는 아직 없어(후속) 늘 빈 배열
 * @param verdict      판결 공유 글에 붙인 내 판결. 없으면 null
 * @param liked        보는 사람이 좋아요를 눌렀는가 (로그인 전이면 false)
 * @param mine         보는 사람이 쓴 글인가 (로그인 전이면 false)
 * @param viewerJudged 보는 사람이 이 글의 사건 판결을 확정했는가. 판결 전이면 화면이 남의 판결 카드를 접는다(FE-93)
 * @param comments     상세(C-2)에서만. 오래된 순
 */
public record PostView(long postId, CommunityCategory category, Long caseId, String caseTitle, AuthorCard author,
	String title, String body, List<String> images, SharedVerdict verdict, int likeCount, boolean liked,
	int commentCount, boolean mine, boolean viewerJudged, OffsetDateTime createdAt,
	@JsonInclude(JsonInclude.Include.NON_NULL) List<CommentView> comments) {

	public PostView withComments(List<CommentView> list) {
		return new PostView(postId, category, caseId, caseTitle, author, title, body, images, verdict, likeCount, liked,
			commentCount, mine, viewerJudged, createdAt, list);
	}
}
