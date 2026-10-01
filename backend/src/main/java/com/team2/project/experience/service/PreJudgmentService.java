package com.team2.project.experience.service;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.dto.OverviewResponse;
import com.team2.project.experience.dto.PreJudgmentRequest;
import com.team2.project.experience.dto.PreJudgmentResponse;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.JudgmentFactor;
import com.team2.project.judgment.repository.JudgmentFactorRepository;
import com.team2.project.judgment.repository.JudgmentRepository;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.RevealStage;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.SentenceRangeOptionRepository;

import lombok.RequiredArgsConstructor;

/** S-03: 사건 개요 조회(API 4)와 사전 판단 제출(API 5). */
@Service
@RequiredArgsConstructor
public class PreJudgmentService {

	/** 사전 판단에 고를 수 있는 판단 요소 수 (확장). */
	private static final int MAX_FACTORS = 2;

	private final MyExperienceService myExperienceService;
	private final ExperienceTransitionService transitionService;
	private final SentenceRangeOptionRepository sentenceRangeOptionRepository;
	private final FactorRepository factorRepository;
	private final JudgmentRepository judgmentRepository;
	private final JudgmentFactorRepository judgmentFactorRepository;
	private final Clock clock;

	/** API 4. 사건 · 체험(404) → 상태(STARTED가 아니면 409) 확인 뒤, DTO 변환까지 읽기 전용 트랜잭션 안에서 한다. */
	@Transactional(readOnly = true)
	public OverviewResponse getOverview(Long caseId, Optional<UUID> anonymousId) {
		Experience experience = myExperienceService.getMyExperienceBetween(caseId, anonymousId,
				ExperienceStatus.STARTED, ExperienceStatus.STARTED);
		LegalCase legalCase = experience.getLegalCase();

		return OverviewResponse.of(
				legalCase,
				sentenceRangeOptionRepository.findAllByCrimeTypeOrderByDisplayOrderAsc(legalCase.getCrimeType()),
				factorRepository.findAllByCaseIdAndRevealStage(caseId, RevealStage.OVERVIEW));
	}

	/** API 5. 검사 순서: 사건 · 체험(404) → 상태(409) → 형량 구간 · 판단 요소(422). */
	@Transactional
	public PreJudgmentResponse submit(Long caseId, Optional<UUID> anonymousId, PreJudgmentRequest request) {
		Experience experience = myExperienceService.getMyExperienceBetween(caseId, anonymousId,
				ExperienceStatus.STARTED, ExperienceStatus.STARTED);
		LegalCase legalCase = experience.getLegalCase();

		List<Long> factorIds = request.factorIdsOrEmpty();
		validateRangeOption(legalCase, request.rangeOptionId());
		validateFactors(caseId, factorIds);

		// 판단 저장과 상태 전이를 한 트랜잭션으로 묶는다. 동시 제출의 패자는 유니크 위반 대신
		// currentStatus가 담긴 INVALID_STATE를 받는다 (ExperienceTransitionService)
		transitionService.apply(experience,
				() -> saveJudgment(experience, request.rangeOptionId(), factorIds),
				e -> e.markPreJudged(clock.instant()));

		// apply가 성공하면 넘긴 엔티티에 전이 결과(PRE_JUDGED, step 1)가 들어 있다 (분리된 상태라 지연 로딩은 쓰지 않는다)
		return new PreJudgmentResponse(experience.getStatus(), experience.getLastReviewedStep());
	}

	/** 검증을 마친 ID라 조회 없이 참조만 걸어 FK 값으로 저장한다 */
	private void saveJudgment(Experience experience, Long rangeOptionId, List<Long> factorIds) {
		Judgment judgment = judgmentRepository.save(Judgment.userPre(experience,
				sentenceRangeOptionRepository.getReferenceById(rangeOptionId)));
		judgmentFactorRepository.saveAll(factorIds.stream()
				.map(factorId -> JudgmentFactor.forPre(judgment, factorRepository.getReferenceById(factorId)))
				.toList());
	}

	private void validateRangeOption(LegalCase legalCase, Long rangeOptionId) {
		if (!sentenceRangeOptionRepository.existsByIdAndCrimeType(rangeOptionId, legalCase.getCrimeType())) {
			throw new BusinessException(ErrorCode.INVALID_RANGE_OPTION);
		}
	}

	private void validateFactors(Long caseId, List<Long> factorIds) {
		if (factorIds.isEmpty()) {
			return;
		}
		if (factorIds.size() > MAX_FACTORS) {
			throw new BusinessException(ErrorCode.TOO_MANY_FACTORS);
		}
		boolean hasDuplicate = new HashSet<>(factorIds).size() != factorIds.size();
		if (hasDuplicate || factorRepository.countByCaseIdAndRevealStageAndIdIn(caseId, RevealStage.OVERVIEW,
				factorIds) != factorIds.size()) {
			throw new BusinessException(ErrorCode.INVALID_FACTOR);
		}
	}
}
