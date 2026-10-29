package com.team2.project.community.service;

import com.team2.project.community.dto.AuthorCard.ShownType;
import com.team2.project.personality.domain.ExperiencePersonality;
import com.team2.project.personality.domain.PersonalityResult;
import com.team2.project.personality.domain.PersonalityScoring;
import com.team2.project.personality.domain.PersonalityScoring.Score;
import com.team2.project.personality.domain.PersonalityType;
import com.team2.project.personality.dto.PersonalityTypeResponse;
import com.team2.project.personality.repository.ExperiencePersonalityRepository;
import com.team2.project.personality.repository.PersonalityResultRepository;
import com.team2.project.personality.repository.PersonalityTypeRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 성향카드 한 장 (BE-115, FE-77 · FE-88). 칸은 마이페이지 성향카드와 같다:
 * 초심(initial) = 첫 성향 테스트, 재심(retake) = 다시 정하기 결과, 최근(current) = 최근 체험 3건 평균의 유형(네 축이 모두 있을 때만).
 * 고른 칸(P-8 shownCard)이 비면 최근 → 초심 → 재심 순으로 대신 고른다(화면 utils/shownCard.js shownSlot과 같다)
 */
@Service
@RequiredArgsConstructor
public class ShownTypeResolver {

	/** 최근 성향에 쓰는 최근 체험 수 (MyPageService와 같다) */
	private static final int RECENT_COUNT = 3;

	private static final List<String> FALLBACK = List.of("current", "initial", "retake");

	private final PersonalityResultRepository resultRepository;
	private final PersonalityTypeRepository typeRepository;
	private final ExperiencePersonalityRepository contributionRepository;

	/** @return 보여 줄 성향이 하나도 없으면 null */
	@Transactional(readOnly = true)
	public ShownType resolve(UUID anonymousUserId, String chosen) {
		Map<String, PersonalityType> slots = new LinkedHashMap<>();
		resultRepository.findFirstByAnonymousUser_IdOrderByCreatedAtAscIdAsc(anonymousUserId)
			.ifPresent(result -> slots.put("initial", result.getType()));
		resultRepository.findByAnonymousUser_IdAndBaselineRetakeTrue(anonymousUserId)
			.map(PersonalityResult::getType)
			.ifPresent(type -> slots.put("retake", type));
		currentType(anonymousUserId).ifPresent(type -> slots.put("current", type));

		String slot = slots.containsKey(chosen) ? chosen
			: FALLBACK.stream().filter(slots::containsKey).findFirst().orElse(null);
		return slot == null ? null : new ShownType(slot, PersonalityTypeResponse.from(slots.get(slot)), null);
	}

	private Optional<PersonalityType> currentType(UUID anonymousUserId) {
		List<ExperiencePersonality> contributions = contributionRepository.findFirstAttemptsByAnonymousUser(anonymousUserId);
		List<ExperiencePersonality> recent = contributions.subList(
			Math.max(0, contributions.size() - RECENT_COUNT), contributions.size());
		return PersonalityScoring.overall(recent.stream().map(ExperiencePersonality::getAxisCounts).toList())
			.map(Score::typeCode)
			.flatMap(typeRepository::findById);
	}
}
