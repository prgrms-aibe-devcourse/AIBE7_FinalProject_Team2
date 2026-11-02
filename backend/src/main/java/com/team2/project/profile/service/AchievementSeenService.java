package com.team2.project.profile.service;

import com.team2.project.profile.repository.AchievementUnlockRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 도전과제 "새로 열림" 확인 (BE-83) */
@Service
@RequiredArgsConstructor
public class AchievementSeenService {

	private final AchievementUnlockRepository repository;
	private final Clock clock;

	/** 이미 본 것 · 해금 기록이 없는 id는 조용히 건너뛴다(멱등) */
	@Transactional
	public void markSeen(long memberId, List<String> achievementIds) {
		repository.markSeen(memberId, achievementIds, clock.instant());
	}
}
