package com.team2.project.admin.community;

import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.community.service.CommunityHomeCache;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 커뮤니티 글 삭제 API (BE-167, A-42). 관리자 체인(세션 · ADMIN 역할 · CSRF)이 지킨다 */
@RestController
@RequestMapping("/api/v1/admin/community/posts")
@RequiredArgsConstructor
public class AdminCommunityPostController {

	private final AdminCommunityPostService postService;
	private final CommunityHomeCache communityHomeCache;

	/** 글 삭제 — 204. 댓글 · 좋아요도 함께 지우고 글쓴이의 경험치는 거두되 레벨은 내리지 않는다. 없는 글 404 */
	@DeleteMapping("/{postId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long postId, @AuthenticationPrincipal AdminPrincipal admin,
		HttpServletRequest request) {
		postService.delete(postId, admin, request);
		communityHomeCache.clear();
	}
}
