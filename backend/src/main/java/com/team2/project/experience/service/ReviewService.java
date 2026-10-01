package com.team2.project.experience.service;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.dto.ReviewResponse;
import com.team2.project.experience.dto.ReviewStepRequest;
import com.team2.project.experience.dto.ReviewStepResponse;
import com.team2.project.experience.service.ExperienceTransitionService.TransitionResult;
import com.team2.project.legalcase.repository.CaseSectionRepository;
import com.team2.project.legalcase.repository.PenaltyRuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사건 정보 조회 · 섹션 확인 (API 6 · 7)
 * 내 체험 찾기 · 상태 범위 확인은 MyExperienceService, 상태 이동은 ExperienceTransitionService를 쓴다 (BE-4)
 */
@Service
@RequiredArgsConstructor
public class ReviewService {
	private final MyExperienceService myExperienceService;
	private final ExperienceTransitionService transitionService;
	private final CaseSectionRepository sectionRepository;
	private final PenaltyRuleRepository ruleRepository;
	private final ReviewResponseAssembler assembler;
	private final Clock clock;

	/** API 6. PRE_JUDGED ~ REVIEWED에서만 볼 수 있다. 아니면 INVALID_STATE(currentStatus 포함) */
	@Transactional(readOnly = true)
	public ReviewResponse getReview(Long caseId, Optional<UUID> anonymousId) {
		Experience experience = myExperienceService.getMyExperienceBetween(caseId, anonymousId,
			ExperienceStatus.PRE_JUDGED, ExperienceStatus.REVIEWED);
		return assembler.assemble(experience.getStatus(), experience.getLastReviewedStep(), experience.getLegalCase(),
			sectionRepository.findAllByCaseId(caseId), ruleRepository.findAllByCaseId(caseId));
	}

	/**
	 * API 7. 이미 확인한 섹션은 거절하지 않고 현재 상태를 그대로 돌려준다 (버튼 두 번 누름 · 동시 요청 대비).
	 * 동시 요청에서 엔티티 값이 실제 현재 값과 다를 수 있으므로 응답은 TransitionResult로 만든다 (BE-4 applyIdempotent).
	 * step 형식(2 ~ 4)은 컨트롤러 @Valid에서 먼저 400으로 거절한다.
	 */
	@Transactional
	public ReviewStepResponse confirmStep(Long caseId, Optional<UUID> anonymousId, ReviewStepRequest request) {
		Experience experience = myExperienceService.getMyExperienceBetween(caseId, anonymousId,
			ExperienceStatus.PRE_JUDGED, ExperienceStatus.REVIEWED);
		Instant now = clock.instant();
		TransitionResult result = transitionService.applyIdempotent(experience,
			target -> target.confirmReviewStep(request.step(), now));
		return new ReviewStepResponse(result.status(), result.lastReviewedStep(),
			ReviewResponseAssembler.openStep(result.lastReviewedStep()));
	}
}
