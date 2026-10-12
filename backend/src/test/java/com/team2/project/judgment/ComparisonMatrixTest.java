package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team2.project.judgment.domain.ComparisonMatrix;
import com.team2.project.judgment.domain.Direction;
import com.team2.project.judgment.domain.JudgmentFactor;
import com.team2.project.judgment.domain.MatrixCategory;
import com.team2.project.judgment.domain.MatrixRow;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.RevealStage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 세 판결 비교 매트릭스 (API 14 matrix, FR-6-4)
 * ERD 6장 예시 데이터(가상 살인 사건)를 그대로 쓴다 — factor 2 ALL_SAME, 6 DIVERGED, 9 ONLY_ME_MISSED는
 * API 명세 "API 14. 세 판결 비교" 예시 응답과 같은 조합이다.
 */
class ComparisonMatrixTest {

	private static Factor factor(long id, String label, int displayOrder) {
		Factor factor = mock(Factor.class);
		when(factor.getId()).thenReturn(id);
		when(factor.getLabel()).thenReturn(label);
		when(factor.getRevealStage()).thenReturn(RevealStage.DETAIL);
		when(factor.getDisplayOrder()).thenReturn(displayOrder);
		return factor;
	}

	private static JudgmentFactor record(Factor factor, Direction direction) {
		JudgmentFactor judgmentFactor = mock(JudgmentFactor.class);
		when(judgmentFactor.getFactor()).thenReturn(factor);
		when(judgmentFactor.getDirection()).thenReturn(direction);
		return judgmentFactor;
	}

	@Test
	void build_allThreeSameDirection_isAllSame() {
		Factor weapon = factor(2, "다투던 중 집에 있던 흉기를 집어 들었다", 2);

		List<MatrixRow> matrix = ComparisonMatrix.build(
			List.of(record(weapon, Direction.UP)),
			List.of(record(weapon, Direction.UP)),
			List.of(record(weapon, Direction.UP)));

		assertThat(matrix).singleElement().satisfies(row -> {
			assertThat(row.factorId()).isEqualTo(2L);
			assertThat(row.user()).isEqualTo(Direction.UP);
			assertThat(row.ai()).isEqualTo(Direction.UP);
			assertThat(row.court()).isEqualTo(Direction.UP);
			assertThat(row.category()).isEqualTo(MatrixCategory.ALL_SAME);
		});
	}

	@Test
	void build_onlyUserMissesWhileAiAndCourtAgree_isOnlyMeMissed() {
		Factor deposit = factor(9, "피해 회복을 위해 5,000만 원을 공탁했다", 9);

		List<MatrixRow> matrix = ComparisonMatrix.build(
			List.of(),
			List.of(record(deposit, Direction.DOWN)),
			List.of(record(deposit, Direction.DOWN)));

		assertThat(matrix).singleElement().satisfies(row -> {
			assertThat(row.user()).isNull();
			assertThat(row.ai()).isEqualTo(Direction.DOWN);
			assertThat(row.court()).isEqualTo(Direction.DOWN);
			assertThat(row.category()).isEqualTo(MatrixCategory.ONLY_ME_MISSED);
		});
	}

	@Test
	void build_aiMissesWhileUserAndCourtAgree_isDiverged() {
		// API 명세 예시: factorId 6, user UP · ai null · court UP → DIVERGED
		Factor children = factor(6, "피해자에게는 부양하던 어린 자녀 2명이 있다", 6);

		List<MatrixRow> matrix = ComparisonMatrix.build(
			List.of(record(children, Direction.UP)),
			List.of(),
			List.of(record(children, Direction.UP)));

		assertThat(matrix).singleElement().extracting(MatrixRow::category).isEqualTo(MatrixCategory.DIVERGED);
	}

	@Test
	void build_allDirectionsDisagree_isDiverged() {
		Factor factor = factor(1, "요소", 1);

		List<MatrixRow> matrix = ComparisonMatrix.build(
			List.of(record(factor, Direction.UP)),
			List.of(record(factor, Direction.DOWN)),
			List.of());

		assertThat(matrix).singleElement().extracting(MatrixRow::category).isEqualTo(MatrixCategory.DIVERGED);
	}

	@Test
	void build_noOneConsidersAnyFactor_returnsEmptyMatrix() {
		// 아무도 고려하지 않은 요소는 행 자체가 없다(ERD 결정 #1) → 매트릭스에서도 자연히 빠진다
		List<MatrixRow> matrix = ComparisonMatrix.build(List.of(), List.of(), List.of());

		assertThat(matrix).isEmpty();
	}

	@Test
	void build_ordersRowsByDisplayOrderRegardlessOfInputOrder() {
		Factor second = factor(2, "두 번째", 2);
		Factor first = factor(1, "첫 번째", 1);

		List<MatrixRow> matrix = ComparisonMatrix.build(
			List.of(record(second, Direction.UP), record(first, Direction.UP)),
			List.of(record(second, Direction.UP), record(first, Direction.UP)),
			List.of(record(second, Direction.UP), record(first, Direction.UP)));

		assertThat(matrix).extracting(MatrixRow::factorId).containsExactly(1L, 2L);
	}
}
