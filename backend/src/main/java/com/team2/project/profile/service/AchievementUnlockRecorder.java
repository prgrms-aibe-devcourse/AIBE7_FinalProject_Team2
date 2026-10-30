package com.team2.project.profile.service;

import com.team2.project.profile.domain.AchievementJudge;
import com.team2.project.profile.domain.ProfileStats;
import com.team2.project.profile.domain.UnlockMark;
import com.team2.project.profile.repository.AchievementUnlockRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도전과제 해금 기록 (BE-83, docs/decisions/BE-83.md). 판정은 저장하지 않고 그때그때 계산하므로,
 * 판정한 쪽이 "지금 CLEARED인데 기록이 없는 것"을 넘겨받아 한 줄씩 남긴다(이미 있으면 무시).
 * 해금 시각은 기록한 때다 — 체험 완료 · 출석처럼 해금을 만드는 요청은 기록하지 않고, 마이페이지 조회 · 수집 기록 때 남긴다
 */
@Service
@RequiredArgsConstructor
public class AchievementUnlockRecorder {

	private final AchievementUnlockRepository repository;
	private final Clock clock;

	/**
	 * 지금 트랜잭션에서 기록한다(수집 기록 POST /collection). 같은 사용자 행을 잠근 트랜잭션이 부르므로
	 * 새 트랜잭션을 열면 FK 검사가 그 잠금을 기다려 교착된다
	 * @param seen true면 "본 것"으로 남긴다(백필)
	 * @return 이번에 새로 남긴 해금 (도전과제 id → 기록)
	 */
	@Transactional
	public Map<String, UnlockMark> record(UUID anonymousUserId, ProfileStats stats, boolean seen) {
		return insertNew(anonymousUserId, stats, seen);
	}

	/**
	 * 새 트랜잭션에서 기록한다(마이페이지 P-5 조회). 조회 조립은 읽기 전용이라 같은 트랜잭션에서 INSERT할 수 없다.
	 * <p>다른 트랜잭션 안에서 부르지 않는다. 바깥 트랜잭션이 연결을 잡은 채 이 트랜잭션이 연결을 하나 더 요청하면,
	 * 풀(운영 5개)만큼 동시에 오는 조회가 서로 두 번째 연결을 기다리며 멈춘다. MyPageService.get은 읽기 트랜잭션을
	 * 닫은 뒤 이것을 부르고, 그다음 응답을 조립한다 (PR #335 리뷰, MyPageUnlockFailureTest가 연결 수를 확인)
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Map<String, UnlockMark> recordSeparately(UUID anonymousUserId, ProfileStats stats) {
		return insertNew(anonymousUserId, stats, false);
	}

	private Map<String, UnlockMark> insertNew(UUID anonymousUserId, ProfileStats stats, boolean seen) {
		Instant now = clock.instant();
		Map<String, UnlockMark> added = new LinkedHashMap<>();
		for (String achievementId : AchievementJudge.unrecordedClears(stats)) {
			// 0이면 같은 순간 다른 요청이 먼저 남겼다 — 그쪽 기록(처음 시각)이 맞으므로 여기서는 더하지 않는다
			if (repository.insertIfAbsent(anonymousUserId, achievementId, now, seen ? now : null) == 1) {
				added.put(achievementId, new UnlockMark(now, seen));
			}
		}
		return added;
	}
}
