package com.team2.project.profile.service;

import com.team2.project.experience.service.AnonymousUserService;
import com.team2.project.profile.repository.AchievementUnlockRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 도전과제 "새로 열림" 확인 (BE-83) */
@Service
@RequiredArgsConstructor
public class AchievementSeenService {

	private final AchievementUnlockRepository repository;
	private final AnonymousUserService anonymousUserService;
	private final Clock clock;

	/** 쿠키가 없거나 모르는 사용자 · 이미 본 것 · 해금 기록이 없는 id는 조용히 건너뛴다(멱등) */
	@Transactional
	public void markSeen(Optional<UUID> anonymousId, List<String> achievementIds) {
		anonymousUserService.findAndTouch(anonymousId)
			.ifPresent(user -> repository.markSeen(user.getId(), achievementIds, clock.instant()));
	}
}
