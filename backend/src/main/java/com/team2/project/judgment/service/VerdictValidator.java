package com.team2.project.judgment.service;

import com.team2.project.judgment.domain.Direction;
import com.team2.project.judgment.domain.InvalidJudgmentException;
import com.team2.project.judgment.domain.InvalidJudgmentException.Reason;
import com.team2.project.judgment.domain.SuspensionRule;
import com.team2.project.judgment.dto.VerdictRequest;
import com.team2.project.legalcase.domain.PenaltyRule;
import com.team2.project.legalcase.domain.PenaltyType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class VerdictValidator {
	public record Option(PenaltyType penaltyType, Long allowedMin, Long allowedMax, boolean suspensionAllowed) {
		public static Option from(PenaltyRule rule) {
			return new Option(rule.getPenaltyType(), rule.getAllowedMin(), rule.getAllowedMax(), rule.isSuspensionAllowed());
		}
	}
	public record ValidFactor(Long factorId, Direction direction) { }
	public record ValidVerdict(PenaltyType penaltyType, PenaltyType reducedTo, Integer prisonMonths,
		Long fineAmount, Integer suspensionMonths, List<ValidFactor> factors) { }

	/** Bean Validation을 통과한 요청만 받는다. 형벌 → 범위 → 집행유예 → 요소 순서로 검사한다. */
	public ValidVerdict validate(VerdictRequest request, List<Option> options, Set<Long> factorIds) {
		PenaltyType selected = parsePenalty(request.penaltyType(), Reason.PENALTY_NOT_OFFERED);
		Option option = options.stream().filter(item -> item.penaltyType() == selected).findFirst()
			.orElseThrow(() -> error(Reason.PENALTY_NOT_OFFERED));
		PenaltyType reduced = request.reducedTo() == null ? null : parsePenalty(request.reducedTo(), Reason.INVALID_REDUCTION);
		if (reduced != null && !selected.canReduceTo(reduced)) throw error(Reason.INVALID_REDUCTION);
		PenaltyType finalType = reduced == null ? selected : reduced;
		validateRange(request, option, finalType);
		validateSuspension(request, option, finalType);
		return new ValidVerdict(selected, reduced, request.prisonMonths(), request.fineAmount(), request.suspensionMonths(),
			validateFactors(request.factors(), factorIds));
	}

	private void validateRange(VerdictRequest request, Option option, PenaltyType finalType) {
		if (finalType.isDeathOrLife()) return;
		long value = finalType == PenaltyType.PRISON ? request.prisonMonths().longValue() : request.fineAmount();
		if (option.allowedMin() == null || option.allowedMax() == null
			|| value < option.allowedMin() || value > option.allowedMax()) {
			throw new InvalidJudgmentException(Reason.OUT_OF_ALLOWED_RANGE, "선고할 수 있는 범위를 벗어났습니다.",
				finalType == PenaltyType.PRISON ? "prisonMonths" : "fineAmount");
		}
	}

	private void validateSuspension(VerdictRequest request, Option option, PenaltyType finalType) {
		Integer months = request.suspensionMonths();
		if (months == null) return;
		if (!option.suspensionAllowed()
			|| (finalType == PenaltyType.PRISON && request.prisonMonths() > SuspensionRule.MAX_PRISON_MONTHS)
			|| (finalType == PenaltyType.FINE && request.fineAmount() > SuspensionRule.MAX_FINE_AMOUNT)
			|| months < SuspensionRule.MIN_MONTHS || months > SuspensionRule.MAX_MONTHS) {
			throw error(Reason.SUSPENSION_CONDITION);
		}
	}

	private List<ValidFactor> validateFactors(List<VerdictRequest.FactorItem> factors, Set<Long> factorIds) {
		Set<Long> seen = new HashSet<>();
		List<ValidFactor> result = new ArrayList<>();
		for (var factor : factors) {
			if (!factorIds.contains(factor.factorId()) || !seen.add(factor.factorId()) || factor.direction() == null) {
				throw error(Reason.INVALID_FACTOR);
			}
			try {
				result.add(new ValidFactor(factor.factorId(), Direction.valueOf(factor.direction())));
			} catch (IllegalArgumentException exception) {
				throw error(Reason.INVALID_FACTOR);
			}
		}
		return List.copyOf(result);
	}

	private PenaltyType parsePenalty(String value, Reason reason) {
		try {
			return PenaltyType.valueOf(value);
		} catch (IllegalArgumentException exception) {
			throw error(reason);
		}
	}

	private InvalidJudgmentException error(Reason reason) {
		return new InvalidJudgmentException(reason, switch (reason) {
			case PENALTY_NOT_OFFERED -> "이 사건에서 선택할 수 없는 형벌입니다.";
			case INVALID_REDUCTION -> "허용되지 않는 감경 조합입니다.";
			case SUSPENSION_CONDITION -> "집행유예 조건을 확인해 주세요.";
			default -> "판단 요소를 확인해 주세요.";
		});
	}
}
