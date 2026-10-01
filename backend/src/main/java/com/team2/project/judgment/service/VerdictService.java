package com.team2.project.judgment.service;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.service.ExperienceTransitionService;
import com.team2.project.experience.service.MyExperienceService;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.JudgmentFactor;
import com.team2.project.judgment.dto.VerdictRequest;
import com.team2.project.judgment.dto.VerdictResponse;
import com.team2.project.judgment.repository.JudgmentFactorRepository;
import com.team2.project.judgment.repository.JudgmentRepository;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.PenaltyRuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판결 제출 (API 9). 검사 순서: 형식(@Valid) → 사건 · 체험 → 상태 → 형벌 → 범위 → 집행유예 → 요소
 * 판단 저장과 상태 이동은 ExperienceTransitionService.apply 한 번으로 묶는다 (BE-4).
 * 동시 중복 제출로 유니크 제약(uk_judgment_experience_timing)을 만나면 apply가 현재 상태로 INVALID_STATE를 던진다.
 */
@Service
@RequiredArgsConstructor
public class VerdictService {
	private final MyExperienceService myExperienceService;
	private final ExperienceTransitionService transitionService;
	private final PenaltyRuleRepository penaltyRuleRepository;
	private final FactorRepository factorRepository;
	private final JudgmentRepository judgmentRepository;
	private final JudgmentFactorRepository judgmentFactorRepository;
	private final VerdictValidator validator;
	private final Clock clock;

	/** Bean Validation(@Valid)을 통과한 요청을 받는다. REVIEWED가 아니면 INVALID_STATE (이미 확정 포함) */
	@Transactional
	public VerdictResponse submit(Long caseId, Optional<UUID> anonymousId, VerdictRequest request) {
		Experience experience = myExperienceService.getMyExperienceBetween(caseId, anonymousId,
			ExperienceStatus.REVIEWED, ExperienceStatus.REVIEWED);
		var options = penaltyRuleRepository.findAllByCaseId(caseId).stream().map(VerdictValidator.Option::from).toList();
		var factors = factorRepository.findAllByCaseId(caseId).stream().collect(Collectors.toMap(Factor::getId, Function.identity()));
		var valid = validator.validate(request, options, factors.keySet());
		Judgment judgment = Judgment.userFinal(experience, valid.penaltyType(), valid.reducedTo(),
			valid.prisonMonths(), valid.fineAmount(), valid.suspensionMonths(), request.freeOpinion());
		Instant now = clock.instant();
		transitionService.apply(experience,
			() -> {
				judgmentRepository.save(judgment);
				judgmentFactorRepository.saveAll(valid.factors().stream()
					.map(factor -> JudgmentFactor.forFinal(judgment, factors.get(factor.factorId()), factor.direction())).toList());
			},
			target -> target.markVerdictConfirmed(now));
		return new VerdictResponse(ExperienceStatus.VERDICT_CONFIRMED);
	}
}
