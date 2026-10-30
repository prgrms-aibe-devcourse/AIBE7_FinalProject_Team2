package com.team2.project.experience.service;

import com.team2.project.common.owner.Owner;
import com.team2.project.common.web.Requester;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요청한 사람 → 체험 · 성향 데이터의 주인 (BE-140). 회원이면 회원이 주인이고 쿠키는 보지 않는다.
 * 손님이면 지금까지처럼 쿠키의 익명 사용자이고, 찾으면 최근 접속 시각을 갱신한다(AnonymousUserService)
 */
@Service
@RequiredArgsConstructor
public class OwnerService {

	private final AnonymousUserService anonymousUserService;

	/** 주인 찾기. 손님인데 쿠키가 없거나 DB에 없는 값이면 empty */
	@Transactional
	public Optional<Owner> find(Requester requester) {
		if (requester.memberId().isPresent()) {
			return requester.owner();
		}
		return anonymousUserService.findAndTouch(requester.anonymousId())
			.map(user -> new Owner.AnonymousOwner(user.getId()));
	}

	/** 주인 찾기 · 없으면 손님 익명 ID 발급(체험 시작 API 2 · 성향 테스트 제출 P-2). 회원은 발급하지 않는다 */
	@Transactional
	public IssuedOwner getOrIssue(Requester requester) {
		if (requester.memberId().isPresent()) {
			return new IssuedOwner(requester.owner().orElseThrow(), null);
		}
		AnonymousUserService.IssuedAnonymousUser issued = anonymousUserService.getOrIssue(requester.anonymousId());
		UUID id = issued.user().getId();
		return new IssuedOwner(new Owner.AnonymousOwner(id), issued.issued() ? id : null);
	}

	/** @param issuedCookieId 익명 ID를 새로 발급했을 때만 값이 있다 (Set-Cookie 대상) */
	public record IssuedOwner(Owner owner, UUID issuedCookieId) {
	}
}
