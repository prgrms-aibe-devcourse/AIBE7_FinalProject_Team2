package com.team2.project.community.service;

import com.team2.project.common.owner.Owner;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.exception.FieldErrorDetail;
import com.team2.project.community.domain.CommunityCategory;
import com.team2.project.community.domain.CommunityComment;
import com.team2.project.community.domain.CommunityPost;
import com.team2.project.community.domain.CommunityText;
import com.team2.project.community.domain.SharedVerdict;
import com.team2.project.community.dto.AuthorCard;
import com.team2.project.community.dto.CommentView;
import com.team2.project.community.dto.CreateCommentRequest;
import com.team2.project.community.dto.CreatePostRequest;
import com.team2.project.community.dto.LikeResponse;
import com.team2.project.community.dto.PostListResponse;
import com.team2.project.community.dto.PostListResponse.CaseLink;
import com.team2.project.community.dto.PostListResponse.PostLink;
import com.team2.project.community.dto.PostView;
import com.team2.project.community.dto.ShareableVerdictResponse;
import com.team2.project.community.repository.CommunityCommentRepository;
import com.team2.project.community.repository.CommunityJudgmentReader;
import com.team2.project.community.repository.CommunityLikeRepository;
import com.team2.project.community.repository.CommunityPostRepository;
import com.team2.project.community.service.AuthorCardAssembler.Writer;
import com.team2.project.community.service.CommunityActorResolver.Actor;
import com.team2.project.community.service.CommunityActorResolver.Viewer;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.judgment.domain.Direction;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.JudgmentFactor;
import com.team2.project.judgment.repository.JudgmentFactorRepository;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import com.team2.project.level.domain.XpSource;
import com.team2.project.level.service.LevelService;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커뮤니티 글 · 댓글 · 좋아요 (API 명세 12장, BE-115 · docs/decisions/BE-115.md). 응답 모양은 화면 목(communityMockApi.js)과 같다.
 * 읽기는 누구나, 쓰기(글 · 댓글 · 좋아요)는 로그인한 회원만 한다(컨트롤러 · SecurityConfig가 막는다).
 * 쓸 때마다 경험치를 준다(LevelService.award, 수급량 · 상한은 BE-106 XpSource 그대로)
 */
@Service
@RequiredArgsConstructor
public class CommunityService {

	/** 목록 한 쪽의 글 수 (화면 PAGE_SIZE와 같다) */
	static final int PAGE_SIZE = 6;
	/** 쪽 번호 상한. 목록은 첫 글부터 page쪽 끝까지 내려 주므로 한 번에 읽는 글 수를 묶어 둔다 */
	static final int MAX_PAGE = 50;

	/** 판결을 확정한 뒤(판결 확정 이후)여야 내 판결을 붙일 수 있고, 남의 판결 카드를 펼쳐 볼 수 있다 */
	static final Set<ExperienceStatus> VERDICT_DONE =
		EnumSet.of(ExperienceStatus.VERDICT_CONFIRMED, ExperienceStatus.AI_REVEALED, ExperienceStatus.COMPLETED);

	/** API 명세 1-1: 시각은 +09:00 오프셋으로 내려준다 */
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private static final PageRequest FIRST = PageRequest.of(0, 1);

	private final CommunityPostRepository postRepository;
	private final CommunityCommentRepository commentRepository;
	private final CommunityLikeRepository likeRepository;
	private final CommunityJudgmentReader judgmentReader;
	private final JudgmentFactorRepository judgmentFactorRepository;
	private final LegalCaseRepository legalCaseRepository;
	private final CommunityActorResolver actorResolver;
	private final AuthorCardAssembler cardAssembler;
	private final LevelService levelService;
	private final Clock clock;

	/** 목록 정렬 */
	enum PostSort {
		LATEST(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
		LIKES(Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
		COMMENTS(Sort.by(Sort.Order.desc("commentCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

		private final Sort sort;

		PostSort(Sort sort) {
			this.sort = sort;
		}
	}

	// ---------- 읽기 ----------

	/** C-1 글 목록. category · sort가 모르는 값이면 400, page는 1 ~ 50으로 맞춘다 */
	@Transactional(readOnly = true)
	public PostListResponse list(String category, Long caseId, String sort, int page, Viewer viewer) {
		Optional<CommunityCategory> categoryFilter = Optional.ofNullable(category)
			.map(value -> CommunityCategory.from(value)
				.orElseThrow(() -> BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "category")));
		PostSort order = sort == null ? PostSort.LATEST : parseSort(sort);
		int pages = Math.min(Math.max(page, 1), MAX_PAGE);

		Specification<CommunityPost> filter = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			categoryFilter.ifPresent(value -> predicates.add(cb.equal(root.get("category"), value)));
			if (caseId != null) {
				predicates.add(cb.equal(root.get("caseId"), caseId));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
		Page<CommunityPost> found = postRepository.findAll(filter, PageRequest.of(0, pages * PAGE_SIZE, order.sort));
		List<LegalCase> published = legalCaseRepository.findAllPublished();
		return new PostListResponse(views(found.getContent(), viewer, titles(published)), found.getTotalElements(),
			found.getTotalElements() > (long) pages * PAGE_SIZE, popular(), caseLinks(published));
	}

	/** C-2 글 + 댓글(오래된 순) */
	@Transactional(readOnly = true)
	public PostView detail(long postId, Viewer viewer) {
		CommunityPost post = findPost(postId);
		List<CommunityComment> comments = commentRepository.findByPostIdOrderByIdAsc(postId);
		Map<Writer, AuthorCard> cards = cardAssembler.cards(Stream.concat(Stream.of(writerOf(post)),
			comments.stream().map(CommunityService::writerOf)).toList());
		Map<Long, String> titles = titles(legalCaseRepository.findAllPublished());
		PostView view = view(post, cards, titles, liked(viewer, List.of(post)), judged(viewer), viewer);
		return view.withComments(comments.stream().map(comment -> commentView(comment, cards, viewer)).toList());
	}

	/** C-7 글에 붙일 수 있는 내 판결: 가장 최근에 확정한 판결. 없으면 empty (판결 확정 전 · 쿠키 없음) */
	@Transactional(readOnly = true)
	public Optional<ShareableVerdictResponse> shareableVerdict(Viewer viewer) {
		return viewer.owner()
			.flatMap(owner -> judgmentReader.findConfirmedFinals(owner, VERDICT_DONE, FIRST).stream().findFirst())
			.map(judgment -> new ShareableVerdictResponse(judgment.getLegalCase().getId(),
				judgment.getLegalCase().getTitle(), sharedVerdict(judgment)));
	}

	// ---------- 쓰기 (로그인 회원) ----------

	/**
	 * C-3 글쓰기. 틀린 칸을 모아 400 VALIDATION_ERROR(details)로 내려 주고, 그때는 아무것도 저장하지 않는다.
	 * shareVerdict면 서버가 내 판결(가장 최근 확정, caseId를 보냈으면 그 사건)을 붙이고 글의 사건을 그 사건으로 맞춘다.
	 * 경험치: 글 작성(POST, 하루 3건) + 판결을 붙였으면 판결 공유 보너스(SHARE_POST, 사건당 1회)
	 */
	@Transactional
	public PostView create(long memberId, Optional<UUID> cookie, CreatePostRequest request) {
		List<FieldErrorDetail> details = new ArrayList<>();
		CommunityCategory category = CommunityCategory.from(request.category()).orElse(null);
		if (category == null) {
			details.add(new FieldErrorDetail("category", "MUST_BE_ONE_OF_CATEGORIES"));
		}
		String title = CommunityText.title(request.title());
		if (title == null) {
			details.add(new FieldErrorDetail("title", "MUST_BE_1_TO_" + CommunityText.TITLE_MAX + "_CHARS"));
		}
		String body = CommunityText.body(request.body(), CommunityText.BODY_MAX);
		if (body == null) {
			details.add(new FieldErrorDetail("body", "MUST_BE_1_TO_" + CommunityText.BODY_MAX + "_CHARS"));
		}
		if (request.images() != null && !request.images().isEmpty()) {
			details.add(new FieldErrorDetail("images", "NOT_SUPPORTED_YET"));	// 사진 올리기는 후속(S3)
		}
		if (request.caseId() != null && legalCaseRepository.findPublishedById(request.caseId()).isEmpty()) {
			details.add(new FieldErrorDetail("caseId", "CASE_NOT_FOUND"));
		}
		rejectIfAny(details);

		Actor actor = actorResolver.writer(memberId, cookie);
		Long caseId = request.caseId();
		SharedVerdict verdict = null;
		if (Boolean.TRUE.equals(request.shareVerdict())) {
			Judgment judgment = (caseId == null
				? judgmentReader.findConfirmedFinals(new Owner.MemberOwner(memberId), VERDICT_DONE, FIRST)
				: judgmentReader.findConfirmedFinalsOfCase(new Owner.MemberOwner(memberId), VERDICT_DONE, caseId, FIRST))
				.stream().findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
					null, List.of(new FieldErrorDetail("shareVerdict", "VERDICT_NOT_CONFIRMED"))));
			verdict = sharedVerdict(judgment);
			caseId = judgment.getLegalCase().getId();	// 판결을 붙이면 그 사건 글이다
		}

		CommunityPost post = postRepository.save(
			CommunityPost.of(memberId, actor.anonymousUserId(), category, caseId, title, body, verdict, clock.instant()));
		levelService.award(memberId, XpSource.POST, String.valueOf(post.getId()), "");
		if (verdict != null) {
			levelService.award(memberId, XpSource.SHARE_POST, String.valueOf(caseId), "");
		}
		Viewer self = new Viewer(Optional.of(memberId), Optional.empty());
		Map<Writer, AuthorCard> cards = cardAssembler.cards(List.of(writerOf(post)));
		return view(post, cards, titles(legalCaseRepository.findAllPublished()), Set.of(), judged(self), self);
	}

	/**
	 * C-4 좋아요: 누를 때마다 켜고 끈다. 켤 때만 경험치를 준다 — 누른 사람 LIKE_GIVEN(글당 1회 · 하루 10개),
	 * 글쓴이 LIKE_RECEIVED(글당 10개 · 하루 30개, 대상 = "글 ID:누른 회원 ID"). 껐다 다시 켜도 같은 대상이라 다시 주지 않는다.
	 * 내 글에 누른 좋아요는 수만 세고 경험치는 주지 않는다(스스로 쌓는 것 방지)
	 */
	@Transactional
	public LikeResponse toggleLike(long memberId, Optional<UUID> cookie, long postId) {
		CommunityPost post = findPost(postId);
		Actor actor = actorResolver.writer(memberId, cookie);
		boolean liked;
		if (likeRepository.insertIfAbsent(postId, memberId) == 1) {
			liked = true;
			postRepository.addLikeCount(postId, 1);
			if (post.getMemberId() != null && post.getMemberId() != memberId) {
				awardLike(memberId, post.getMemberId(), postId);
			}
		} else {
			liked = false;
			if (likeRepository.deleteOne(postId, memberId) == 1) {
				postRepository.addLikeCount(postId, -1);
			}
		}
		return new LikeResponse(liked, postRepository.findLikeCount(postId));
	}

	/** C-5 댓글. 경험치: 댓글 작성(COMMENT, 하루 5건) */
	@Transactional
	public CommentView comment(long memberId, Optional<UUID> cookie, long postId, CreateCommentRequest request) {
		String body = CommunityText.body(request.body(), CommunityText.COMMENT_MAX);
		if (body == null) {
			rejectIfAny(List.of(new FieldErrorDetail("body", "MUST_BE_1_TO_" + CommunityText.COMMENT_MAX + "_CHARS")));
		}
		findPost(postId);
		Actor actor = actorResolver.writer(memberId, cookie);
		CommunityComment comment = commentRepository.save(
			CommunityComment.of(postId, memberId, actor.anonymousUserId(), body, clock.instant()));
		postRepository.incrementCommentCount(postId);
		levelService.award(memberId, XpSource.COMMENT, String.valueOf(comment.getId()), "");
		Map<Writer, AuthorCard> cards = cardAssembler.cards(List.of(writerOf(comment)));
		return commentView(comment, cards, new Viewer(Optional.of(memberId), Optional.empty()));
	}

	// ---------- 도우미 ----------

	/**
	 * 좋아요 경험치 두 건. award는 회원 행을 잠그므로, 두 사람이 서로의 글에 동시에 누르면 잠금 순서가 엇갈려 교착이 날 수 있다 —
	 * 회원 ID 순서로 차례를 고정한다(BE-140: 예전에는 익명 ID 순서)
	 */
	private void awardLike(long likerId, long authorId, long postId) {
		Runnable given = () -> levelService.award(likerId, XpSource.LIKE_GIVEN, String.valueOf(postId), "");
		Runnable received = () -> levelService.award(authorId, XpSource.LIKE_RECEIVED, postId + ":" + likerId,
			String.valueOf(postId));
		if (likerId <= authorId) {
			given.run();
			received.run();
		} else {
			received.run();
			given.run();
		}
	}

	private List<PostView> views(List<CommunityPost> posts, Viewer viewer, Map<Long, String> titles) {
		if (posts.isEmpty()) {
			return List.of();
		}
		Map<Writer, AuthorCard> cards = cardAssembler.cards(posts.stream().map(CommunityService::writerOf).toList());
		Set<Long> liked = liked(viewer, posts);
		Set<Long> judged = judged(viewer);
		return posts.stream().map(post -> view(post, cards, titles, liked, judged, viewer)).toList();
	}

	private PostView view(CommunityPost post, Map<Writer, AuthorCard> cards, Map<Long, String> titles, Set<Long> liked,
		Set<Long> judged, Viewer viewer) {
		return new PostView(post.getId(), post.getCategory(), post.getCaseId(),
			post.getCaseId() == null ? null : titles.get(post.getCaseId()), cards.get(writerOf(post)), post.getTitle(),
			post.getBody(), List.of(), post.getVerdict(), post.getLikeCount(), liked.contains(post.getId()),
			post.getCommentCount(), isMine(viewer, post.getMemberId()), post.getCaseId() != null && judged.contains(post.getCaseId()),
			kst(post.getCreatedAt()), null);
	}

	private CommentView commentView(CommunityComment comment, Map<Writer, AuthorCard> cards, Viewer viewer) {
		return new CommentView(comment.getId(), cards.get(writerOf(comment)), comment.getBody(), kst(comment.getCreatedAt()),
			isMine(viewer, comment.getMemberId()));
	}

	private static boolean isMine(Viewer viewer, Long memberId) {
		return viewer.memberId().map(id -> id.equals(memberId)).orElse(false);
	}

	private Set<Long> liked(Viewer viewer, List<CommunityPost> posts) {
		if (viewer.memberId().isEmpty() || posts.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(likeRepository.findLikedPostIds(viewer.memberId().get(),
			posts.stream().map(CommunityPost::getId).toList()));
	}

	private Set<Long> judged(Viewer viewer) {
		return viewer.owner()
			.map(owner -> (Set<Long>) new HashSet<>(judgmentReader.findJudgedCaseIds(owner, VERDICT_DONE)))
			.orElse(Set.of());
	}

	private SharedVerdict sharedVerdict(Judgment judgment) {
		List<JudgmentFactor> factors = judgmentFactorRepository.findAllByJudgmentIds(List.of(judgment.getId()));
		int up = (int) factors.stream().filter(factor -> factor.getDirection() == Direction.UP).count();
		int down = (int) factors.stream().filter(factor -> factor.getDirection() == Direction.DOWN).count();
		return new SharedVerdict(judgment.getPenaltyType() == null ? null : judgment.getPenaltyType().name(),
			judgment.getReducedTo() == null ? null : judgment.getReducedTo().name(), judgment.getPrisonMonths(),
			judgment.getFineAmount(), judgment.getSuspensionMonths(), up, down);
	}

	List<PostLink> popular() {
		return postRepository.findTop3ByOrderByLikeCountDescCreatedAtDescIdDesc().stream()
			.map(post -> new PostLink(post.getId(), post.getTitle())).toList();
	}

	static List<CaseLink> caseLinks(List<LegalCase> published) {
		return published.stream().sorted(Comparator.comparing(LegalCase::getId))
			.map(legalCase -> new CaseLink(legalCase.getId(), legalCase.getTitle())).toList();
	}

	private static Map<Long, String> titles(List<LegalCase> published) {
		return published.stream().collect(Collectors.toMap(LegalCase::getId, LegalCase::getTitle));
	}

	private CommunityPost findPost(long postId) {
		return postRepository.findById(postId).orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
	}

	private static PostSort parseSort(String value) {
		try {
			return PostSort.valueOf(value);
		} catch (IllegalArgumentException e) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "sort");
		}
	}

	private static void rejectIfAny(List<FieldErrorDetail> details) {
		if (!details.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), null, details);
		}
	}

	static Writer writerOf(CommunityPost post) {
		return new Writer(post.getMemberId());
	}

	static Writer writerOf(CommunityComment comment) {
		return new Writer(comment.getMemberId());
	}

	static OffsetDateTime kst(Instant instant) {
		return instant.atZone(KST).toOffsetDateTime();
	}
}
