package com.team2.project.judgment.service;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.JudgmentFactor;
import com.team2.project.judgment.dto.VerdictRequest;
import com.team2.project.judgment.dto.VerdictResponse;
import com.team2.project.judgment.repository.JudgmentFactorRepository;
import com.team2.project.judgment.repository.JudgmentRepository;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.PenaltyRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerdictService {
	private final ExperienceRepository experienceRepository;
	private final PenaltyRuleRepository penaltyRuleRepository;
	private final FactorRepository factorRepository;
	private final JudgmentRepository judgmentRepository;
	private final JudgmentFactorRepository judgmentFactorRepository;
	private final VerdictValidator validator;

	/** 소유권 · 존재 확인 및 Bean Validation을 통과한 체험 ID와 요청을 받는다. */
	@Transactional
	public VerdictResponse submit(Long experienceId, VerdictRequest request) {
		Experience experience = experienceRepository.findById(experienceId)
			.orElseThrow(() -> new EntityNotFoundException("체험을 찾을 수 없습니다."));
		if (experience.getStatus() != ExperienceStatus.REVIEWED) {
			throw new InvalidExperienceStateException(experience.getStatus(), "지금 단계에서는 판결을 확정할 수 없습니다.");
		}
		Long caseId = experience.getLegalCase().getId();
		var options = penaltyRuleRepository.findAllByCaseId(caseId).stream().map(VerdictValidator.Option::from).toList();
		var factors = factorRepository.findAllByCaseId(caseId).stream().collect(Collectors.toMap(Factor::getId, Function.identity()));
		var valid = validator.validate(request, options, factors.keySet());
		Judgment judgment = Judgment.userFinal(experience, valid.penaltyType(), valid.reducedTo(),
			valid.prisonMonths(), valid.fineAmount(), valid.suspensionMonths(), request.freeOpinion());
		try {
			judgmentRepository.saveAndFlush(judgment);
		} catch (DataIntegrityViolationException exception) {
			if (!isDuplicateFinal(exception)) throw exception;
			throw new InvalidExperienceStateException(ExperienceStatus.VERDICT_CONFIRMED, "이미 판결을 확정했습니다.");
		}
		judgmentFactorRepository.saveAll(valid.factors().stream()
			.map(factor -> JudgmentFactor.forFinal(judgment, factors.get(factor.factorId()), factor.direction())).toList());
		experience.markVerdictConfirmed(Instant.now());
		return new VerdictResponse(experience.getStatus());
	}

	private boolean isDuplicateFinal(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
				&& "uk_judgment_experience_timing".equals(violation.getConstraintName())) return true;
		}
		return false;
	}
}
