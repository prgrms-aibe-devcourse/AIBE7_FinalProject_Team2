package com.team2.project.judgment.service;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.judgment.domain.SuspensionRule;
import com.team2.project.judgment.dto.VerdictFormResponse;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.PenaltyRangeText;
import com.team2.project.legalcase.domain.PenaltyType;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.PenaltyRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerdictFormService {
	private final ExperienceRepository experienceRepository;
	private final PenaltyRuleRepository penaltyRuleRepository;
	private final FactorRepository factorRepository;

	/** 소유권 · 404 검사는 2단계 진입 계층에서 수행하고 확인된 체험 ID를 전달한다. */
	@Transactional(readOnly = true)
	public VerdictFormResponse getForm(Long experienceId) {
		Experience experience = experienceRepository.findById(experienceId)
			.orElseThrow(() -> new EntityNotFoundException("체험을 찾을 수 없습니다."));
		if (experience.getStatus() != ExperienceStatus.REVIEWED) {
			throw new InvalidExperienceStateException(experience.getStatus(), "사건 정보를 모두 확인한 뒤 판결할 수 있습니다.");
		}
		LegalCase legalCase = experience.getLegalCase();
		var options = penaltyRuleRepository.findAllByCaseId(legalCase.getId()).stream()
			.map(rule -> new VerdictFormResponse.PenaltyOption(rule.getPenaltyType(), rule.getAllowedMin(),
				rule.getAllowedMax(), PenaltyRangeText.format(rule.getPenaltyType(), rule.getAllowedMin(), rule.getAllowedMax()),
				rule.isSuspensionAllowed(), Arrays.stream(PenaltyType.values()).filter(rule.getPenaltyType()::canReduceTo).toList()))
			.toList();
		var factors = factorRepository.findAllByCaseId(legalCase.getId()).stream()
			.map(factor -> new VerdictFormResponse.FactorItem(factor.getId(), factor.getLabel())).toList();
		return new VerdictFormResponse(options, legalCase.getStatutoryPenaltyText(),
			"감경·가중 사유를 반영해 법률상 선고할 수 있는 가장 넓은 범위예요.",
			new VerdictFormResponse.Recommended(legalCase.getRecommendedMinMonths(), legalCase.getRecommendedMaxMonths(),
				legalCase.getRecommendedBasis()),
			new VerdictFormResponse.Suspension(SuspensionRule.MAX_PRISON_MONTHS, SuspensionRule.MAX_FINE_AMOUNT,
				SuspensionRule.MIN_MONTHS, SuspensionRule.MAX_MONTHS), factors);
	}
}
