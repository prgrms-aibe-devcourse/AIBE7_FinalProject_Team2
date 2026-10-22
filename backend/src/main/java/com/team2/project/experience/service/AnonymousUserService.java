package com.team2.project.experience.service;

import com.team2.project.attendance.service.AttendanceRecorder;
import com.team2.project.experience.domain.AnonymousUser;
import com.team2.project.experience.repository.AnonymousUserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 익명 사용자 조회 · 발급 (API 명세 1-2)
 * 쿠키 읽기 · 쓰기는 AnonymousIdCookie가 하고, 여기서는 쿠키 값으로 DB의 익명 사용자를 다룬다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnonymousUserService {

	/** 최근 접속 시각은 이 간격이 지났을 때만 갱신한다 (조회 요청마다 UPDATE 방지) */
	static final Duration TOUCH_INTERVAL = Duration.ofMinutes(10);

	private final AnonymousUserRepository anonymousUserRepository;

	private final Clock clock;

	private final AttendanceRecorder attendanceRecorder;

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
	 * 쿠키의 익명 사용자가 있으면 그대로, 없거나 DB에 없는 값이면 새로 발급한다(체험 시작 API 2 · 성향 테스트 제출 P-2 · 꾸미기 저장 P-7).
	 * issued가 true면 컨트롤러가 Set-Cookie로 새 익명 ID를 내려준다.
	 * 새로 발급하면 그날 출석도 함께 남긴다(BE-98): 앱 시작 때 부르는 출석 API는 쿠키가 생기기 전이라 첫 방문날이 빠지기 때문이다.
	 * 발급은 사용자가 실제로 행동했을 때만 일어나 아무것도 하지 않은 방문자 · 봇은 기록되지 않는다(BE-97 원칙 유지)
	 */
	@Transactional
	public IssuedAnonymousUser getOrIssue(Optional<UUID> anonymousId) {
		return findAndTouch(anonymousId)
			.map(user -> new IssuedAnonymousUser(user, false))
			.orElseGet(this::issue);
	}

	private IssuedAnonymousUser issue() {
		// 출석은 직접 쓴 SQL이라, 외래 키가 가리킬 익명 사용자 행을 먼저 DB에 내보낸다
		AnonymousUser user = anonymousUserRepository.saveAndFlush(AnonymousUser.issue(clock.instant()));
		attendanceRecorder.record(user.getId());
		return new IssuedAnonymousUser(user, true);
	}

	/** 조회 또는 발급 결과 */
	public record IssuedAnonymousUser(AnonymousUser user, boolean issued) {
	}
}
