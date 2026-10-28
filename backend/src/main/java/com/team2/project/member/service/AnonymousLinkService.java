package com.team2.project.member.service;

import com.team2.project.experience.domain.AnonymousUser;
import com.team2.project.experience.repository.AnonymousUserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 회원에 익명 기록(익명 ID 쿠키) 연결 (확장 단계, BE-62 결정 6 · BE-113에서 카카오 · 아이디 로그인 공용으로 분리)
 *
 * 규칙 (docs/decisions/BE-62.md):
 * - 쿠키의 익명 ID가 아직 연결 안 됨 → 이 회원에 연결
 * - 이미 같은 회원에 연결됨 → 그대로
 * - 다른 회원에 연결됨(공용 PC) → 덮어쓰지 않고, 이 회원에 연결된 새 익명 ID를 발급한다.
 *   그대로 두면 이후 체험이 앞 사람 계정으로 쌓이기 때문이다
 * - 쿠키가 없거나 DB에 없는 값 → 이 회원에 연결된 새 익명 ID를 발급한다
 * 같은 회원이 다른 브라우저에서 로그인하면 그 브라우저의 익명 ID도 연결된다(회원 1 : 익명 N).
 *
 * 회원 행 처리와 같은 트랜잭션 안에서만 부른다(MANDATORY) — 회원 처리가 실패하면 연결도 되돌린다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnonymousLinkService {

	private final AnonymousUserRepository anonymousUserRepository;

	/** @return 새로 발급한 익명 ID(있으면 호출한 쪽이 쿠키로 바꿔 심는다) */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<UUID> link(long memberId, Optional<UUID> anonymousId, Instant now) {
		if (anonymousId.isPresent()) {
			UUID id = anonymousId.get();
			if (anonymousUserRepository.linkIfUnlinked(id, memberId) == 1) {
				return Optional.empty();
			}
			Optional<Long> owner = anonymousUserRepository.findById(id).map(AnonymousUser::getMemberId);
			if (owner.isPresent() && owner.get() == memberId) {
				return Optional.empty();
			}
			if (owner.isPresent()) {
				log.info("다른 회원에 연결된 익명 기록이라 연결하지 않고 새 익명 ID를 발급한다");
			}
		}
		AnonymousUser fresh = anonymousUserRepository.save(AnonymousUser.issueFor(memberId, now));
		return Optional.of(fresh.getId());
	}
}
