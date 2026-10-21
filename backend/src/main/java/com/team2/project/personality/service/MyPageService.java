package com.team2.project.personality.service;

import com.team2.project.attendance.service.AttendanceService;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.experience.service.AnonymousUserService;
import com.team2.project.legalcase.domain.ValueAxis;
import com.team2.project.personality.domain.AxisCount;
import com.team2.project.personality.domain.ExperiencePersonality;
import com.team2.project.personality.domain.PersonalityScoring;
import com.team2.project.personality.dto.MyPageResponse;
import com.team2.project.personality.dto.MyPageResponse.ExperienceItem;
import com.team2.project.personality.dto.MyPageResponse.HistoryItem;
import com.team2.project.personality.dto.MyPageResponse.JudgmentPersonality;
import com.team2.project.personality.dto.MyPageResponse.Profile;
import com.team2.project.personality.dto.PersonalityResultResponse;
import com.team2.project.personality.dto.PersonalityTypeResponse;
import com.team2.project.personality.repository.ExperiencePersonalityRepository;
import com.team2.project.personality.repository.PersonalityResultRepository;
import com.team2.project.personality.repository.PersonalityTypeRepository;
import com.team2.project.profile.service.ProfileDecoService;
import com.team2.project.profile.service.ProfileDecoService.ProfileDecoView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * P-5 마이페이지 (API 명세 9장). 종합 · 흐름은 각 사건의 첫 회차 체험 기여(experience_personality)로 계산한다(성향 산식 4장).
 * 종합(현재 판결 성향)은 최근 3건(RECENT_COUNT)만 평균한다(API 명세 v0.44, BE-99). 프론트 목(personalityMockApi.js judgmentOf)과 같은 규칙이다
 */
@Service
@RequiredArgsConstructor
public class MyPageService {

	/** 현재 판결 성향에 쓰는 최근 체험 수 */
	private static final int RECENT_COUNT = 3;

	private final AnonymousUserService anonymousUserService;
	private final PersonalityResultRepository resultRepository;
	private final PersonalityTypeRepository typeRepository;
	private final ExperiencePersonalityRepository contributionRepository;
	private final ExperienceRepository experienceRepository;
	private final AttendanceService attendanceService;
	private final ProfileDecoService profileDecoService;

	@Transactional(readOnly = true)
	public MyPageResponse get(Optional<UUID> anonymousId) {
		return anonymousUserService.findAndTouch(anonymousId)
			.map(user -> build(user.getId()))
			.orElseGet(MyPageResponse::empty);	// 쿠키가 없거나 모르는 ID면 빈 마이페이지 (404가 아니다)
	}

	private MyPageResponse build(UUID userId) {
		PersonalityResultResponse personality = resultRepository.findByAnonymousUser_IdAndCurrentTrue(userId)
			.map(PersonalityResultResponse::from).orElse(null);

		List<ExperiencePersonality> contributions = contributionRepository.findFirstAttemptsByAnonymousUser(userId);
		// 현재 판결 성향은 완료 순서(쿼리의 completedAt, id 순)로 최근 3건만 평균한다. 흐름(history)은 전체를 내려준다
		List<ExperiencePersonality> recent = contributions.subList(
			Math.max(0, contributions.size() - RECENT_COUNT), contributions.size());
		List<Map<ValueAxis, AxisCount>> counts = recent.stream().map(ExperiencePersonality::getAxisCounts).toList();
		List<HistoryItem> history = contributions.stream()
			.map(contribution -> HistoryItem.of(contribution.getExperience(), contribution.getCreatedAt(),
				PersonalityScoring.experienceScores(contribution.getAxisCounts())))
			.toList();
		JudgmentPersonality judgment = PersonalityScoring.overall(counts)
			.map(score -> new JudgmentPersonality(score.axisScores(),
				score.typeCode() == null ? null
					: typeRepository.findById(score.typeCode()).map(PersonalityTypeResponse::from).orElse(null),
				recent.size()))	// 평균에 쓴 체험 수(최대 3). 축 자료가 모두 빈 체험도 센다. API 명세 P-5 v0.44, #128 리뷰
			.orElse(null);

		List<ExperienceItem> experiences = experienceRepository.findLatestPerCase(userId).stream()
			.map(ExperienceItem::from).toList();
		ProfileDecoView deco = profileDecoService.view(userId);
		return new MyPageResponse(personality, judgment, history, experiences,
			new Profile(attendanceService.countDays(userId)), deco.profileDeco(), deco.achievements(), deco.unlocked());
	}
}
