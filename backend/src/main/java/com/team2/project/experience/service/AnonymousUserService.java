package com.team2.project.experience.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team2.project.experience.domain.AnonymousUser;
import com.team2.project.experience.repository.AnonymousUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnonymousUserService {

	private final AnonymousUserRepository anonymousUserRepository;

	/** 쿠키의 익명 ID가 DB에 있으면 last_seen_at만 갱신하고, 없거나 쿠키가 없으면 새 익명 사용자를 만든다. */
	@Transactional
	public ResolvedAnonymousUser resolveOrIssue(UUID cookieId) {
		Instant now = Instant.now();
		if (cookieId != null && anonymousUserRepository.touch(cookieId, now) > 0) {
			return new ResolvedAnonymousUser(cookieId, false);
		}
		// 클라이언트가 보낸 값을 그대로 채택하지 않고 서버가 새 ID를 생성한다
		AnonymousUser issued = anonymousUserRepository.save(AnonymousUser.issue(now));
		return new ResolvedAnonymousUser(issued.getId(), true);
	}

	/** 쿠키의 익명 ID가 DB에 있으면 last_seen_at을 갱신하고 true를 돌려준다. */
	@Transactional
	public boolean touch(UUID cookieId) {
		return cookieId != null && anonymousUserRepository.touch(cookieId, Instant.now()) > 0;
	}

	/** issued가 true면 쿠키를 새로 내려줘야 한다. */
	public record ResolvedAnonymousUser(UUID id, boolean issued) {
	}
}
