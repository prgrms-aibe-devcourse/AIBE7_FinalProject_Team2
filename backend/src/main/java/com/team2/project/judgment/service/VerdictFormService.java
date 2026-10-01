package com.team2.project.judgment.service;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.service.MyExperienceService;
import com.team2.project.judgment.domain.SuspensionRule;
import com.team2.project.judgment.dto.VerdictFormResponse;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.PenaltyRangeText;
import com.team2.project.legalcase.domain.PenaltyType;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.PenaltyRuleRepository;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerdictFormService {
	private final MyExperienceService myExperienceService;
	private final PenaltyRuleRepository penaltyRuleRepository;
	private final FactorRepository factorRepository;

	/** API 8. REVIEWED에서만 볼 수 있다. 아니면 INVALID_STATE(currentStatus 포함) */
	@Transactional(readOnly = true)
	public VerdictFormResponse getForm(Long caseId, Optional<UUID> anonymousId) {
		Experience experience = myExperienceService.getMyExperienceBetween(caseId, anonymousId,
			ExperienceStatus.REVIEWED, ExperienceStatus.REVIEWED);
		LegalCase legalCase = experience.getLegalCase();
		var options = penaltyRuleRepository.findAllByCaseId(caseId).stream()
			.map(rule -> new VerdictFormResponse.PenaltyOption(rule.getPenaltyType(), rule.getAllowedMin(),
				rule.getAllowedMax(), PenaltyRangeText.format(rule.getPenaltyType(), rule.getAllowedMin(), rule.getAllowedMax()),
				rule.isSuspensionAllowed(), Arrays.stream(PenaltyType.values()).filter(rule.getPenaltyType()::canReduceTo).toList()))
			.toList();
		var factors = factorRepository.findAllByCaseId(caseId).stream()
			.map(factor -> new VerdictFormResponse.FactorItem(factor.getId(), factor.getLabel())).toList();
		return new VerdictFormResponse(options, legalCase.getStatutoryPenaltyText(),
			"감경·가중 사유를 반영해 법률상 선고할 수 있는 가장 넓은 범위예요.",
			new VerdictFormResponse.Recommended(legalCase.getRecommendedMinMonths(), legalCase.getRecommendedMaxMonths(),
				legalCase.getRecommendedBasis()),
			new VerdictFormResponse.Suspension(SuspensionRule.MAX_PRISON_MONTHS, SuspensionRule.MAX_FINE_AMOUNT,
				SuspensionRule.MIN_MONTHS, SuspensionRule.MAX_MONTHS), factors);
	}
}
