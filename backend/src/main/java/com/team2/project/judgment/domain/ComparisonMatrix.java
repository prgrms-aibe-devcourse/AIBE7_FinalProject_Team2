package com.team2.project.judgment.domain;

import com.team2.project.legalcase.domain.Factor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 세 판결(USER · AI · COURT)의 최종 판단 요소 기록을 하나의 매트릭스로 합친다 (API 14 matrix, FR-6-4)
 * 셋 다 고려하지 않은 요소(모두 null)는 뺀다 (ERD 6장). 행은 요소 표시 순서(display_order)로 정렬한다.
 */
public final class ComparisonMatrix {

	private ComparisonMatrix() {
	}

	public static List<MatrixRow> build(List<JudgmentFactor> user, List<JudgmentFactor> ai, List<JudgmentFactor> court) {
		Map<Long, Factor> factorsById = new LinkedHashMap<>();
		Map<Long, Direction> userDirections = directionsOf(user, factorsById);
		Map<Long, Direction> aiDirections = directionsOf(ai, factorsById);
		Map<Long, Direction> courtDirections = directionsOf(court, factorsById);

		List<MatrixRow> rows = new ArrayList<>();
		for (Factor factor : factorsById.values()) {
			Direction userDirection = userDirections.get(factor.getId());
			Direction aiDirection = aiDirections.get(factor.getId());
			Direction courtDirection = courtDirections.get(factor.getId());
			if (userDirection == null && aiDirection == null && courtDirection == null) {
				continue;
			}
			rows.add(new MatrixRow(factor.getId(), factor.getLabel(), factor.getRevealStage(),
				userDirection, aiDirection, courtDirection, category(userDirection, aiDirection, courtDirection)));
		}
		rows.sort(Comparator.comparingInt(row -> factorsById.get(row.factorId()).getDisplayOrder()));
		return rows;
	}

	private static Map<Long, Direction> directionsOf(List<JudgmentFactor> factors, Map<Long, Factor> factorsById) {
		Map<Long, Direction> result = new LinkedHashMap<>();
		for (JudgmentFactor judgmentFactor : factors) {
			Factor factor = judgmentFactor.getFactor();
			factorsById.putIfAbsent(factor.getId(), factor);
			result.put(factor.getId(), judgmentFactor.getDirection());
		}
		return result;
	}

	/** matrix[].category 분류 (API 명세 6장) */
	private static MatrixCategory category(Direction user, Direction ai, Direction court) {
		if (user == ai && ai == court) {
			// 여기 도달했다면 셋 다 null은 아니다(호출 쪽에서 이미 걸렀다) → 셋 다 같은 방향
			return MatrixCategory.ALL_SAME;
		}
		if (user == null && ai != null && ai == court) {
			return MatrixCategory.ONLY_ME_MISSED;
		}
		return MatrixCategory.DIVERGED;
	}
}
