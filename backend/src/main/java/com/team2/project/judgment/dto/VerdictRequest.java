package com.team2.project.judgment.dto;

import com.team2.project.legalcase.domain.PenaltyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record VerdictRequest(
	@NotBlank String penaltyType,
	String reducedTo,
	@PositiveOrZero Integer prisonMonths,
	@PositiveOrZero Long fineAmount,
	@PositiveOrZero Integer suspensionMonths,
	List<@NotNull @Valid FactorItem> factors,
	// TODO: REQ-033 확장 시 null 전용 대신 1,000자 제한을 적용한다.
	@Null String freeOpinion
) {
	public VerdictRequest {
		// null 원소는 생성 시 예외 대신 Bean Validation에서 형식 오류로 처리한다.
		factors = factors == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(factors));
	}

	public record FactorItem(@NotNull Long factorId, String direction) { }

	@AssertTrue(message = "형벌 종류에 맞는 형량 값만 입력해야 합니다.")
	public boolean isTermCombinationValid() {
		PenaltyType selected;
		PenaltyType reduced;
		try {
			if (penaltyType == null) return true; // @NotBlank에서 처리
			selected = PenaltyType.valueOf(penaltyType);
			reduced = reducedTo == null ? null : PenaltyType.valueOf(reducedTo);
		} catch (IllegalArgumentException exception) {
			return true; // 알 수 없는 형벌은 비즈니스 검증에서 422로 처리
		}
		if (reduced != null && !selected.canReduceTo(reduced)) return true;
		return switch (reduced == null ? selected : reduced) {
			case PRISON -> prisonMonths != null && fineAmount == null;
			case FINE -> fineAmount != null && prisonMonths == null;
			case DEATH, LIFE -> prisonMonths == null && fineAmount == null && suspensionMonths == null;
		};
	}
}
