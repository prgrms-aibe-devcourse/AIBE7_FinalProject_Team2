package com.team2.project.personality.service;

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
import com.team2.project.personality.dto.PersonalityResultResponse;
import com.team2.project.personality.dto.PersonalityTypeResponse;
import com.team2.project.personality.repository.ExperiencePersonalityRepository;
import com.team2.project.personality.repository.PersonalityResultRepository;
import com.team2.project.personality.repository.PersonalityTypeRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * P-5 마이페이지 (API 명세 9장). 종합 · 흐름은 각 사건의 첫 회차 체험 기여(experience_personality)로 계산한다(성향 산식 4장)
 */
@Service
@RequiredArgsConstructor
public class MyPageService {

	private final AnonymousUserService anonymousUserService;
	private final PersonalityResultRepository resultRepository;
	private final PersonalityTypeRepository typeRepository;
	private final ExperiencePersonalityRepository contributionRepository;
	private final ExperienceRepository experienceRepository;

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
		List<Map<ValueAxis, AxisCount>> counts = contributions.stream().map(ExperiencePersonality::getAxisCounts).toList();
		List<HistoryItem> history = contributions.stream()
			.map(contribution -> HistoryItem.of(contribution.getExperience(), contribution.getCreatedAt(),
				PersonalityScoring.experienceScores(contribution.getAxisCounts())))
			.toList();
		JudgmentPersonality judgment = PersonalityScoring.overall(counts)
			.map(score -> new JudgmentPersonality(score.axisScores(),
				score.typeCode() == null ? null
					: typeRepository.findById(score.typeCode()).map(PersonalityTypeResponse::from).orElse(null),
				contributions.size()))	// 평균을 낸 체험과 같은 집합(기여가 저장된 첫 회차). API 명세 P-5, #128 리뷰
			.orElse(null);

		List<ExperienceItem> experiences = experienceRepository.findLatestPerCase(userId).stream()
			.map(ExperienceItem::from).toList();
		return new MyPageResponse(personality, judgment, history, experiences);
	}
}
