package com.team2.project.experience.service;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
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
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.JudgmentFactor;
import com.team2.project.judgment.repository.JudgmentFactorRepository;
import com.team2.project.judgment.repository.JudgmentRepository;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.RevealStage;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.SentenceRangeOptionRepository;
import com.team2.project.legalcase.service.LegalCaseService;

import lombok.RequiredArgsConstructor;

/** S-03: 사건 개요 조회(API 4)와 사전 판단 제출(API 5). */
@Service
@RequiredArgsConstructor
public class PreJudgmentService {

	/** 사전 판단에 고를 수 있는 판단 요소 수 (확장). */
	static final int MAX_FACTORS = 2;

	/** 섹션 ① 개요는 S-03에서 본 것으로 처리한다. */
	static final int LAST_REVIEWED_STEP_AFTER_PRE_JUDGMENT = 1;

	private final LegalCaseService legalCaseService;
	private final ExperienceService experienceService;
	private final ExperienceRepository experienceRepository;
	private final SentenceRangeOptionRepository sentenceRangeOptionRepository;
	private final FactorRepository factorRepository;
	private final JudgmentRepository judgmentRepository;
	private final JudgmentFactorRepository judgmentFactorRepository;

	/**
	 * API 4. 읽기 전용 트랜잭션으로 묶지 않는다: 익명 사용자의 last_seen_at을 갱신하는 UPDATE가 함께 실행되는데,
	 * PostgreSQL은 읽기 전용 트랜잭션 안의 UPDATE를 거절한다.
	 */
	@Transactional
	public OverviewResponse getOverview(Long caseId, UUID cookieId) {
		LegalCase legalCase = legalCaseService.getPublished(caseId);
		requireStarted(experienceService.findMine(caseId, cookieId));

		return OverviewResponse.of(
				legalCase,
				sentenceRangeOptionRepository.findByCrimeTypeOrderByDisplayOrder(legalCase.getCrimeType()),
				factorRepository.findByCaseIdAndRevealStageOrderByDisplayOrder(caseId, RevealStage.OVERVIEW));
	}

	/** API 5. 검사 순서: 사건 · 체험(404) → 상태(409) → 형량 구간 · 판단 요소(422). */
	@Transactional
	public PreJudgmentResponse submit(Long caseId, UUID cookieId, PreJudgmentRequest request) {
		LegalCase legalCase = legalCaseService.getPublished(caseId);
		Experience experience = experienceService.findMine(caseId, cookieId);
		requireStarted(experience);

		List<Long> factorIds = request.factorIdsOrEmpty();
		validateRangeOption(legalCase, request.rangeOptionId());
		validateFactors(caseId, factorIds);

		// 저장보다 먼저 상태를 조건부로 바꿔서 동시에 온 요청 중 하나만 통과시킨다.
		// 뒤의 저장이 실패하면 이 갱신도 함께 롤백된다.
		int updated = experienceRepository.advanceToPreJudged(experience.getId(), ExperienceStatus.STARTED,
				ExperienceStatus.PRE_JUDGED, LAST_REVIEWED_STEP_AFTER_PRE_JUDGMENT, OffsetDateTime.now());
		if (updated == 0) {
			throw new BusinessException(ErrorCode.INVALID_STATE, currentStatusOf(experience));
		}

		Judgment judgment = judgmentRepository
				.save(Judgment.userPreJudgment(caseId, experience.getId(), request.rangeOptionId()));
		judgmentFactorRepository
				.saveAll(factorIds.stream().map(factorId -> new JudgmentFactor(judgment.getId(), factorId)).toList());

		return new PreJudgmentResponse(ExperienceStatus.PRE_JUDGED, LAST_REVIEWED_STEP_AFTER_PRE_JUDGMENT);
	}

	private void requireStarted(Experience experience) {
		if (experience.getStatus() != ExperienceStatus.STARTED) {
			throw new BusinessException(ErrorCode.INVALID_STATE, experience.getStatus().name());
		}
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

	/** 상태 갱신이 0건이면 그 사이 다른 요청이 상태를 바꾼 것이다. 바뀐 현재 상태를 다시 읽어 알려 준다. */
	private String currentStatusOf(Experience experience) {
		return experienceRepository.findById(experience.getId())
				.map(latest -> latest.getStatus().name())
				.orElse(experience.getStatus().name());
	}
}
