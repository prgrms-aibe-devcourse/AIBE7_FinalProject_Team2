package com.team2.project.experience.service;

import com.team2.project.experience.domain.AnonymousUser;
import com.team2.project.experience.repository.AnonymousUserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 익명 사용자 조회 · 발급 (API 명세 1-2)
 * 쿠키 읽기 · 쓰기는 AnonymousIdCookie가 하고, 여기서는 쿠키 값으로 DB의 익명 사용자를 다룬다.
 */
@Service
@RequiredArgsConstructor
public class AnonymousUserService {

	/** 최근 접속 시각은 이 간격이 지났을 때만 갱신한다 (조회 요청마다 UPDATE 방지) */
	static final Duration TOUCH_INTERVAL = Duration.ofMinutes(10);

	private final AnonymousUserRepository anonymousUserRepository;

	private final Clock clock;

	/** 쿠키 값의 익명 사용자. 쿠키가 없거나 DB에 없는 값이면 empty. 찾으면 최근 접속 시각을 갱신한다 */
	@Transactional
	public Optional<AnonymousUser> findAndTouch(Optional<UUID> anonymousId) {
		Instant now = clock.instant();
		return anonymousId
			.flatMap(anonymousUserRepository::findById)
			.map(user -> {
				anonymousUserRepository.touchIfStale(user.getId(), now, now.minus(TOUCH_INTERVAL));
				return user;
			});
	}

	/**
	 * 체험 시작(API 2) 전용: 쿠키의 익명 사용자가 있으면 그대로, 없거나 DB에 없는 값이면 새로 발급한다.
	 * issued가 true면 컨트롤러가 Set-Cookie로 새 익명 ID를 내려준다.
	 */
	@Transactional
	public IssuedAnonymousUser getOrIssue(Optional<UUID> anonymousId) {
		return findAndTouch(anonymousId)
			.map(user -> new IssuedAnonymousUser(user, false))
			.orElseGet(() -> new IssuedAnonymousUser(anonymousUserRepository.save(AnonymousUser.issue(clock.instant())), true));
	}

	/** 조회 또는 발급 결과 */
	public record IssuedAnonymousUser(AnonymousUser user, boolean issued) {
	}
}
