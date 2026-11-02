package com.team2.project.common.web;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.owner.Owner;
import java.util.Optional;
import java.util.UUID;

/**
 * 요청을 보낸 사람 (BE-140). 컨트롤러는 쿠키 · 토큰을 직접 읽지 않고 이 값을 인자로 받는다(RequesterArgumentResolver).
 *
 * @param memberId    로그인한 회원. 토큰이 없거나 회원 토큰이 아니면 empty
 * @param anonymousId 이 브라우저의 익명 ID(쿠키 NLNB_AID). 쿠키가 없거나 UUID가 아니면 empty
 */
public record Requester(Optional<Long> memberId, Optional<UUID> anonymousId) {

	/** 로그인하지 않은 손님. anonymousId는 쿠키 값 */
	public static Requester guest(Optional<UUID> anonymousId) {
		return new Requester(Optional.empty(), anonymousId);
	}

	/** 쿠키를 가진 손님 */
	public static Requester guest(UUID anonymousId) {
		return guest(Optional.of(anonymousId));
	}

	/** 로그인한 회원. 쿠키가 있어도 주인은 회원이다 */
	public static Requester member(long memberId) {
		return new Requester(Optional.of(memberId), Optional.empty());
	}

	/** 회원 전용 API의 회원 ID. 로그인 전이면 401 UNAUTHORIZED */
	public long requireMember() {
		return memberId.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}

	/**
	 * 체험 · 성향 데이터의 주인: 로그인했으면 회원(쿠키의 익명 ID는 보지 않는다), 아니면 쿠키의 익명 ID. 둘 다 없으면 empty.
	 * 익명 ID가 DB에 있는지는 보지 않는다 — 읽기만 하는 곳은 없는 값이면 행이 없어 그대로 비고, 확인이 필요한 곳은 OwnerService를 쓴다
	 */
	public Optional<Owner> owner() {
		return memberId.<Owner>map(Owner.MemberOwner::new).or(() -> anonymousId.map(Owner.AnonymousOwner::new));
	}
}
