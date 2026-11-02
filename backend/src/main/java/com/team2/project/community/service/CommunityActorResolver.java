package com.team2.project.community.service;

import com.team2.project.common.owner.Owner;
import com.team2.project.experience.domain.AnonymousUser;
import com.team2.project.experience.repository.AnonymousUserRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커뮤니티 요청을 누가 했는지 정한다 (BE-115).
 * 글쓴이는 회원이고, 명함 · 경험치는 지금 구조대로 익명 ID 기준이라 회원에 연결된 익명 ID 하나를 함께 고른다(docs/decisions/BE-115.md):
 * 쿠키의 익명 ID가 이 회원에 연결돼 있으면 그것, 아니면 이 회원에 연결된 익명 ID 중 가장 최근에 접속한 것.
 * 마이페이지 · 레벨 데이터를 회원 기준으로 옮기면(후속 이슈) 이 클래스만 바꾸면 된다
 */
@Service
@RequiredArgsConstructor
public class CommunityActorResolver {

	private final AnonymousUserRepository anonymousUserRepository;
	private final Clock clock;

	/** 쓰기 요청을 한 사람. anonymousUserId는 DB에 있는 익명 ID로 글 · 댓글에 남긴다(되돌림 때 이전 코드가 읽는다, V43). 명함 · 경험치 · 판결은 회원 기준이다(BE-140) */
	public record Actor(long memberId, UUID anonymousUserId) {
	}

	/**
	 * 보는 사람. 로그인했으면 회원이 기준이고(anonymousUserId는 비어 있다), 로그인 전이면 memberId가 없고
	 * 판결 여부(viewerJudged)는 쿠키의 익명 ID로 본다
	 */
	public record Viewer(Optional<Long> memberId, Optional<UUID> anonymousUserId) {

		/** 체험 · 판결을 읽는 기준: 회원이면 회원, 아니면 쿠키의 익명 ID (BE-140) */
		public Optional<Owner> owner() {
			return memberId.<Owner>map(Owner.MemberOwner::new).or(() -> anonymousUserId.map(Owner.AnonymousOwner::new));
		}
	}

	/** 로그인 회원의 쓰기 요청. 연결된 익명 ID가 하나도 없으면(로그인 처리가 늘 만들어 두므로 드물다) 이 회원에 연결된 익명 ID를 새로 만든다 */
	@Transactional
	public Actor writer(long memberId, Optional<UUID> cookie) {
		UUID anonymousUserId = linkedAnonymousId(memberId, cookie)
			.orElseGet(() -> anonymousUserRepository.saveAndFlush(AnonymousUser.issueFor(memberId, clock.instant())).getId());
		return new Actor(memberId, anonymousUserId);
	}

	@Transactional(readOnly = true)
	public Viewer viewer(Optional<Long> memberId, Optional<UUID> cookie) {
		Optional<UUID> anonymousUserId = memberId.isPresent()
			? Optional.empty()
			: cookie.flatMap(anonymousUserRepository::findById).map(AnonymousUser::getId);
		return new Viewer(memberId, anonymousUserId);
	}

	private Optional<UUID> linkedAnonymousId(long memberId, Optional<UUID> cookie) {
		return cookie.flatMap(anonymousUserRepository::findById)
			.filter(user -> Objects.equals(user.getMemberId(), memberId))
			.or(() -> anonymousUserRepository.findFirstByMemberIdOrderByLastSeenAtDesc(memberId))
			.map(AnonymousUser::getId);
	}
}
