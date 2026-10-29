package com.team2.project.community.service;

import com.team2.project.community.config.CommunityProperties;
import com.team2.project.community.dto.CommunityHomeResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 커뮤니티 메인(C-6) 응답 캐시 (BE-115 리뷰 반영, docs/decisions/BE-115.md).
 * 메인은 로그인 없이 누구나 부르고 글쓴이 전원의 명함을 계산하므로, 계산한 응답을 {@link CommunityProperties#homeCacheTtl()} 동안
 * 그대로 준다. 응답에 보는 사람에 따라 달라지는 값이 없어 모두가 같은 응답을 공유한다.
 * <ul>
 *   <li>서버 한 대의 메모리에만 둔다(서버가 여러 대면 대마다 따로 계산한다). 재시작하면 비워진다</li>
 *   <li>만료되면 한 요청만 다시 계산하고, 그동안 다른 요청은 기다렸다가 같은 결과를 받는다(동시에 여럿이 계산하지 않는다)</li>
 *   <li>계산에 실패하면 예외를 그대로 내고 캐시는 옛 값을 유지한다</li>
 * </ul>
 * 계산은 {@link CommunityHomeService#home()}이 읽기 전용 트랜잭션 안에서 한다 — 이 클래스는 트랜잭션 밖이다
 */
@Component
public class CommunityHomeCache {

	private final CommunityHomeService homeService;
	private final CommunityProperties properties;
	private final Clock clock;

	private record Snapshot(CommunityHomeResponse response, Instant computedAt) {
	}

	private volatile Snapshot snapshot;

	public CommunityHomeCache(CommunityHomeService homeService, CommunityProperties properties, Clock clock) {
		this.homeService = homeService;
		this.properties = properties;
		this.clock = clock;
	}

	public CommunityHomeResponse home() {
		Duration ttl = properties.homeCacheTtl();
		if (ttl.isZero()) {
			return homeService.home();
		}
		Snapshot current = snapshot;
		if (isFresh(current, ttl)) {
			return current.response();
		}
		synchronized (this) {
			current = snapshot;
			if (isFresh(current, ttl)) {	// 기다리는 동안 다른 요청이 이미 계산했다
				return current.response();
			}
			Instant now = clock.instant();
			CommunityHomeResponse computed = homeService.home();
			snapshot = new Snapshot(computed, now);
			return computed;
		}
	}

	/** 캐시를 비운다(다음 요청이 다시 계산한다). 운영 도구 · 시험용 */
	public void clear() {
		snapshot = null;
	}

	private boolean isFresh(Snapshot current, Duration ttl) {
		return current != null && current.computedAt().plus(ttl).isAfter(clock.instant());
	}
}
