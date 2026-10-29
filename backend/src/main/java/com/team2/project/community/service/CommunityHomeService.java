package com.team2.project.community.service;

import com.team2.project.community.domain.CommunityPost;
import com.team2.project.community.dto.AuthorCard;
import com.team2.project.community.dto.CommunityHomeResponse;
import com.team2.project.community.dto.CommunityHomeResponse.CaseBlock;
import com.team2.project.community.dto.CommunityHomeResponse.CasePost;
import com.team2.project.community.dto.CommunityHomeResponse.CaseSummary;
import com.team2.project.community.dto.CommunityHomeResponse.RankingItem;
import com.team2.project.community.repository.CommunityPostRepository;
import com.team2.project.community.repository.CommunityPostRepository.CaseCount;
import com.team2.project.community.service.AuthorCardAssembler.Writer;
import com.team2.project.experience.repository.CaseParticipantCount;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.legalcase.domain.CasePaper;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.repository.CasePaperRepository;
import com.team2.project.legalcase.repository.CaseScriptRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import java.text.Collator;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C-6 커뮤니티 메인 (FE-89, BE-115). 지금 이야기가 많은 사건 · 추천 사건 · 레벨 랭킹 + 많이 본 글 · 공개 사건.
 * <ul>
 *   <li>이야기 수(talkCount): 최근 하루의 그 사건 글 + 그 사건 글에 달린 댓글 수. 1 이상인 사건을 많은 순으로 셋(같으면 사건 ID 순)</li>
 *   <li>추천 사건: 이야기 많은 사건에 없는 공개 사건을 이야기 수 → 판결한 사람 수 순으로 셋</li>
 *   <li>레벨 랭킹: 글이나 댓글을 쓴 회원을 레벨 → 체험한 사건 수 → 닉네임 순으로 열 명. 레벨은 사건 체험 · 커뮤니티 활동으로 오르며 성향과 무관하다</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CommunityHomeService {

	static final int HOT_SIZE = 3;
	static final int RECOMMENDED_SIZE = 3;
	static final int RANKING_SIZE = 10;
	private static final int HOT_POSTS = 3;
	private static final int RECOMMENDED_POSTS = 2;
	/** 메인에 싣는 기사 본문 단락 수 */
	private static final int ARTICLE_PARAGRAPHS = 2;
	private static final Duration TALK_WINDOW = Duration.ofDays(1);

	private final CommunityPostRepository postRepository;
	private final LegalCaseRepository legalCaseRepository;
	private final CasePaperRepository casePaperRepository;
	private final CaseScriptRepository caseScriptRepository;
	private final ExperienceRepository experienceRepository;
	private final AuthorCardAssembler cardAssembler;
	private final CommunityService communityService;
	private final Clock clock;

	@Transactional(readOnly = true)
	public CommunityHomeResponse home() {
		List<LegalCase> published = legalCaseRepository.findAllPublished();
		Map<Long, Long> talk = postRepository.countTalkByCaseSince(clock.instant().minus(TALK_WINDOW)).stream()
			.collect(Collectors.toMap(CaseCount::getCaseId, CaseCount::getCount));
		Map<Long, Long> participants = experienceRepository.countCompletedFirstAttemptsByCase().stream()
			.collect(Collectors.toMap(CaseParticipantCount::getCaseId, CaseParticipantCount::getParticipantCount));

		List<LegalCase> hot = published.stream()
			.filter(legalCase -> talk.getOrDefault(legalCase.getId(), 0L) > 0)
			.sorted(Comparator.comparingLong((LegalCase legalCase) -> talk.get(legalCase.getId())).reversed()
				.thenComparing(LegalCase::getId))
			.limit(HOT_SIZE).toList();
		Set<Long> hotIds = hot.stream().map(LegalCase::getId).collect(Collectors.toSet());
		List<LegalCase> recommended = published.stream()
			.filter(legalCase -> !hotIds.contains(legalCase.getId()))
			.sorted(Comparator.comparingLong((LegalCase legalCase) -> talk.getOrDefault(legalCase.getId(), 0L)).reversed()
				.thenComparing(Comparator.comparingLong(
					(LegalCase legalCase) -> participants.getOrDefault(legalCase.getId(), 0L)).reversed())
				.thenComparing(LegalCase::getId))
			.limit(RECOMMENDED_SIZE).toList();

		List<Long> shownIds = Stream.concat(hot.stream(), recommended.stream()).map(LegalCase::getId).toList();
		Map<Long, Long> postCounts = shownIds.isEmpty() ? Map.of()
			: postRepository.countPostsByCase(shownIds).stream()
				.collect(Collectors.toMap(CaseCount::getCaseId, CaseCount::getCount));
		Map<Long, List<CommunityPost>> casePosts = Stream.concat(
				hot.stream().map(legalCase -> Map.entry(legalCase.getId(), HOT_POSTS)),
				recommended.stream().map(legalCase -> Map.entry(legalCase.getId(), RECOMMENDED_POSTS)))
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> postRepository
				.findByCaseIdOrderByLikeCountDescCreatedAtDescIdDesc(entry.getKey(), PageRequest.of(0, entry.getValue()))));

		List<Writer> rankingWriters = postRepository.findLatestWriters().stream()
			.map(ref -> new Writer(ref.getMemberId(), ref.getAnonymousUserId())).toList();
		List<Writer> allWriters = new ArrayList<>(rankingWriters);
		casePosts.values().forEach(posts -> posts.forEach(post -> allWriters.add(CommunityService.writerOf(post))));
		Map<Writer, AuthorCard> cards = cardAssembler.cards(allWriters);

		Set<Long> scriptReady = caseScriptRepository.findApprovedCaseIds();
		Function<LegalCase, CaseSummary> summary = legalCase -> summary(legalCase,
			participants.getOrDefault(legalCase.getId(), 0L), scriptReady.contains(legalCase.getId()));
		List<CaseBlock> hotBlocks = hot.stream().map(legalCase -> new CaseBlock(summary.apply(legalCase),
			talk.get(legalCase.getId()), postCounts.getOrDefault(legalCase.getId(), 0L),
			casePostsOf(casePosts.get(legalCase.getId()), cards))).toList();
		List<CaseBlock> recommendedBlocks = recommended.stream().map(legalCase -> new CaseBlock(summary.apply(legalCase),
			null, postCounts.getOrDefault(legalCase.getId(), 0L),
			casePostsOf(casePosts.get(legalCase.getId()), cards))).toList();

		return new CommunityHomeResponse(hotBlocks, recommendedBlocks, ranking(rankingWriters, cards),
			communityService.popular(), CommunityService.caseLinks(published));
	}

	private CaseSummary summary(LegalCase legalCase, long participantCount, boolean scriptReady) {
		CasePaper paper = casePaperRepository.findByLegalCaseId(legalCase.getId()).orElse(null);
		List<String> article = paper == null || paper.getArticle() == null ? List.of()
			: paper.getArticle().stream().limit(ARTICLE_PARAGRAPHS).toList();
		return new CaseSummary(legalCase.getId(), legalCase.getTitle(), legalCase.getCrimeType().getCategoryLabel(),
			legalCase.getShortIntro(), paper == null ? null : paper.getHeadline(), paper == null ? null : paper.getDeck(),
			article, legalCase.getThumbnailUrl(), participantCount, legalCase.getEstimatedMinutes(), scriptReady);
	}

	private static List<CasePost> casePostsOf(List<CommunityPost> posts, Map<Writer, AuthorCard> cards) {
		return posts.stream().map(post -> new CasePost(post.getId(), post.getTitle(), post.getLikeCount(),
			post.getCommentCount(), cards.get(CommunityService.writerOf(post)))).toList();
	}

	private static List<RankingItem> ranking(List<Writer> writers, Map<Writer, AuthorCard> cards) {
		Collator korean = Collator.getInstance(Locale.KOREAN);
		List<AuthorCard> sorted = writers.stream().map(cards::get)
			.sorted(Comparator.comparingInt(AuthorCard::level).reversed()
				.thenComparing(Comparator.comparingInt(AuthorCard::completedCount).reversed())
				.thenComparing(AuthorCard::nickname, korean))
			.limit(RANKING_SIZE).toList();
		return IntStream.range(0, sorted.size()).mapToObj(index -> {
			AuthorCard card = sorted.get(index);
			return new RankingItem(index + 1, card.nickname(), card.level(), card.job(), card.cardLine(),
				card.completedCount(), card.profileCard(), card.shownType());
		}).toList();
	}
}
