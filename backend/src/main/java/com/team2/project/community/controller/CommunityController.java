package com.team2.project.community.controller;

import com.team2.project.common.web.Requester;
import com.team2.project.community.dto.CommentView;
import com.team2.project.community.dto.CommunityHomeResponse;
import com.team2.project.community.dto.CreateCommentRequest;
import com.team2.project.community.dto.CreatePostRequest;
import com.team2.project.community.dto.LikeResponse;
import com.team2.project.community.dto.PostListResponse;
import com.team2.project.community.dto.PostView;
import com.team2.project.community.dto.ShareableVerdictResponse;
import com.team2.project.community.service.CommunityActorResolver;
import com.team2.project.community.service.CommunityActorResolver.Viewer;
import com.team2.project.community.service.CommunityHomeCache;
import com.team2.project.community.service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 커뮤니티 (API 명세 12장 C-1 ~ C-7, BE-115). 읽기는 누구나, 쓰기는 로그인한 회원만(Authorization: Bearer).
 * 쓰기 경로는 SecurityConfig가 먼저 막고(401 UNAUTHORIZED), 여기서도 회원이 없으면 같은 401로 끝낸다.
 * 익명 ID 쿠키는 발급하지 않고 읽기만 한다. 명함 · 경험치 기준 익명 ID는 CommunityActorResolver가 회원에서 고르고,
 * 연결된 익명 ID가 없는 회원이면 그 회원에 연결된 익명 ID를 새로 만든다(쿠키로 내려 주지는 않는다)
 */
@RestController
@RequestMapping("/api/v1/community")
@RequiredArgsConstructor
public class CommunityController {

	private final CommunityService communityService;
	private final CommunityHomeCache communityHomeCache;
	private final CommunityActorResolver actorResolver;

	/** C-1 글 목록 */
	@GetMapping("/posts")
	public PostListResponse posts(@RequestParam(required = false) String category,
		@RequestParam(required = false) Long caseId, @RequestParam(required = false) String sort,
		@RequestParam(defaultValue = "1") int page, Requester requester) {
		return communityService.list(category, caseId, sort, page, viewer(requester));
	}

	/** C-2 글 + 댓글 */
	@GetMapping("/posts/{postId}")
	public PostView post(@PathVariable long postId, Requester requester) {
		return communityService.detail(postId, viewer(requester));
	}

	/** C-3 글쓰기 — 201 + 쓴 글 */
	@PostMapping("/posts")
	@ResponseStatus(HttpStatus.CREATED)
	public PostView create(@RequestBody CreatePostRequest body, Requester requester) {
		return communityService.create(requester.requireMember(), requester.anonymousId(), body);
	}

	/** C-4 좋아요 켜고 끄기 */
	@PostMapping("/posts/{postId}/like")
	public LikeResponse like(@PathVariable long postId, Requester requester) {
		return communityService.toggleLike(requester.requireMember(), requester.anonymousId(), postId);
	}

	/** C-5 댓글 — 201 + 쓴 댓글 */
	@PostMapping("/posts/{postId}/comments")
	@ResponseStatus(HttpStatus.CREATED)
	public CommentView comment(@PathVariable long postId, @RequestBody CreateCommentRequest body,
		Requester requester) {
		return communityService.comment(requester.requireMember(), requester.anonymousId(), postId, body);
	}

	/** C-6 커뮤니티 메인 — 계산한 응답을 일정 시간 그대로 준다(CommunityHomeCache) */
	@GetMapping("/home")
	public CommunityHomeResponse home() {
		return communityHomeCache.home();
	}

	/** C-7 글에 붙일 수 있는 내 판결 — 200, 판결 확정 전이면 204 */
	@GetMapping("/shareable-verdict")
	public ResponseEntity<ShareableVerdictResponse> shareableVerdict(Requester requester) {
		return communityService.shareableVerdict(viewer(requester))
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	private Viewer viewer(Requester requester) {
		return actorResolver.viewer(requester.memberId(), requester.anonymousId());
	}
}
