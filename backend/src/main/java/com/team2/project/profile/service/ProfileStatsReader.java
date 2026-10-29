package com.team2.project.profile.service;

import com.team2.project.attendance.service.AttendanceService;
import com.team2.project.collection.repository.CollectionItemRepository;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.personality.repository.PersonalityResultRepository;
import com.team2.project.profile.domain.AchievementUnlock;
import com.team2.project.profile.domain.ProfileStats;
import com.team2.project.profile.domain.UnlockMark;
import com.team2.project.profile.repository.AchievementUnlockRepository;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도전과제 판정에 쓰는 사용자 값을 DB에서 센다 (BE-96). 진행도는 저장하지 않는다.
 * 모은 일러스트 수(collected)는 collection_item 행 수, 해금 기록은 achievement_unlock이다 (BE-83)
 */
@Service
@RequiredArgsConstructor
public class ProfileStatsReader {

	private final ExperienceRepository experienceRepository;
	private final PersonalityResultRepository personalityResultRepository;
	private final AttendanceService attendanceService;
	private final CollectionItemRepository collectionItemRepository;
	private final AchievementUnlockRepository achievementUnlockRepository;

	@Transactional(readOnly = true)
	public ProfileStats read(UUID anonymousUserId) {
		Map<String, UnlockMark> unlocks = achievementUnlockRepository.findByIdAnonymousUserId(anonymousUserId).stream()
			.collect(Collectors.toMap(unlock -> unlock.getId().getAchievementId(), AchievementUnlock::mark));
		return new ProfileStats(
			Math.toIntExact(experienceRepository.countCompletedCases(anonymousUserId)),
			personalityResultRepository.existsByAnonymousUser_IdAndCurrentTrue(anonymousUserId),
			attendanceService.countDays(anonymousUserId),
			Math.toIntExact(collectionItemRepository.countByIdAnonymousUserId(anonymousUserId)),
			unlocks);
	}
}
