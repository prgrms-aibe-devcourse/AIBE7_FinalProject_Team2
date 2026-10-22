package com.team2.project.profile.service;

import com.team2.project.attendance.service.AttendanceService;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.personality.repository.PersonalityResultRepository;
import com.team2.project.profile.domain.ProfileStats;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도전과제 판정에 쓰는 사용자 값을 DB에서 센다 (BE-96). 진행도는 저장하지 않는다.
 * 모은 일러스트 수(collected)는 사건 대본이 서버에 없어 셀 수 없다 → null(수집 도전과제 PENDING, BE-83 전)
 */
@Service
@RequiredArgsConstructor
public class ProfileStatsReader {

	private final ExperienceRepository experienceRepository;
	private final PersonalityResultRepository personalityResultRepository;
	private final AttendanceService attendanceService;

	@Transactional(readOnly = true)
	public ProfileStats read(UUID anonymousUserId) {
		return new ProfileStats(
			Math.toIntExact(experienceRepository.countCompletedCases(anonymousUserId)),
			personalityResultRepository.existsByAnonymousUser_IdAndCurrentTrue(anonymousUserId),
			attendanceService.countDays(anonymousUserId),
			null);
	}
}
