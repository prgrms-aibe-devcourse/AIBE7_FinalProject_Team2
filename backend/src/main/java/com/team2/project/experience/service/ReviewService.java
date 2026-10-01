package com.team2.project.experience.service;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import com.team2.project.experience.dto.ReviewResponse;
import com.team2.project.experience.dto.ReviewStepRequest;
import com.team2.project.experience.dto.ReviewStepResponse;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.legalcase.repository.CaseSectionRepository;
import com.team2.project.legalcase.repository.PenaltyRuleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {
	private final EntityManager entityManager;
	private final ExperienceRepository experienceRepository;
	private final CaseSectionRepository sectionRepository;
	private final PenaltyRuleRepository ruleRepository;
	private final ReviewResponseAssembler assembler;

	@Transactional(readOnly = true)
	public ReviewResponse getReview(Long experienceId) {
		Experience experience = experienceRepository.findById(experienceId)
			.orElseThrow(() -> new EntityNotFoundException("체험을 찾을 수 없습니다."));
		ExperienceStatus status = experience.getStatus();
		if (status != ExperienceStatus.PRE_JUDGED && status != ExperienceStatus.REVIEWING && status != ExperienceStatus.REVIEWED) {
			throw new InvalidExperienceStateException(status, "지금 단계에서는 사건 정보를 확인할 수 없습니다.");
		}
		var legalCase = experience.getLegalCase();
		return assembler.assemble(status, experience.getLastReviewedStep(), legalCase,
			sectionRepository.findAllByCaseId(legalCase.getId()), ruleRepository.findAllByCaseId(legalCase.getId()));
	}

	/** Bean Validation을 통과한 요청(step != null)을 받는다. 소유권·404 처리는 2단계에서 연결한다. */
	@Transactional
	public ReviewStepResponse confirmStep(Long experienceId, ReviewStepRequest request) {
		// 늦은 중복 요청이 다음 단계 결과를 덮어쓰지 않도록 읽기 전에 잠근다.
		// 뒤 요청은 앞 트랜잭션의 커밋을 기다린 뒤 최신 상태에서 '이미 확인함' 여부를 검사한다.
		Experience experience = entityManager.find(Experience.class, experienceId, LockModeType.PESSIMISTIC_WRITE);
		if (experience == null) throw new EntityNotFoundException("체험을 찾을 수 없습니다.");
		experience.confirmReviewStep(request.step(), Instant.now());
		return new ReviewStepResponse(experience.getStatus(), experience.getLastReviewedStep(),
			ReviewResponseAssembler.openStep(experience.getLastReviewedStep()));
	}
}
