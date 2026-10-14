package com.team2.project.personality;

import static com.team2.project.legalcase.domain.ValueAxis.APOLOGY_SINCERITY;
import static com.team2.project.legalcase.domain.ValueAxis.FAULT_STANDARD;
import static com.team2.project.legalcase.domain.ValueAxis.ORDER_OPPORTUNITY;
import static com.team2.project.legalcase.domain.ValueAxis.PRINCIPLE_RELATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.team2.project.legalcase.domain.ValueAxis;
import com.team2.project.personality.domain.AxisCount;
import com.team2.project.personality.domain.PersonalityScoring;
import com.team2.project.personality.domain.PersonalityScoring.Pick;
import com.team2.project.personality.domain.PersonalityScoring.Score;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 성향 계산 단위 테스트 (BE-72). 예시는 docs/personality-scoring.md의 계산 예시(2-4 · 3-5 · 4장)를 그대로 옮겼다.
 */
class PersonalityScoringTest {

	@Test
	@DisplayName("테스트 채점: 산식 2-4 예시 그대로 — INTP, 축 점수 0.5 · 0.75 · 0.25 · 0.6875")
	void scoreTest_documentExample() {
		Score score = PersonalityScoring.scoreTest(List.of(
			// 사과와 진정성: 매우 E · 조금 I · 보통 · 조금 I → L = S = 2, 동점이라 관대 I
			new Pick(APOLOGY_SINCERITY, "E", 2), new Pick(APOLOGY_SINCERITY, "I", 1),
			new Pick(APOLOGY_SINCERITY, null, 0), new Pick(APOLOGY_SINCERITY, "I", 1),
			// 잘잘못의 기준: 매우 N · 조금 N · 조금 S · 매우 N → L 5, S 1
			new Pick(FAULT_STANDARD, "N", 2), new Pick(FAULT_STANDARD, "N", 1),
			new Pick(FAULT_STANDARD, "S", 1), new Pick(FAULT_STANDARD, "N", 2),
			// 원칙과 관계: 매우 T · 매우 T · 조금 F · 조금 T → L 1, S 5
			new Pick(PRINCIPLE_RELATION, "T", 2), new Pick(PRINCIPLE_RELATION, "T", 2),
			new Pick(PRINCIPLE_RELATION, "F", 1), new Pick(PRINCIPLE_RELATION, "T", 1),
			// 질서와 기회: 조금 P · 조금 P · 조금 J · 매우 P → L 4, S 1
			new Pick(ORDER_OPPORTUNITY, "P", 1), new Pick(ORDER_OPPORTUNITY, "P", 1),
			new Pick(ORDER_OPPORTUNITY, "J", 1), new Pick(ORDER_OPPORTUNITY, "P", 2)));

		assertThat(score.typeCode()).isEqualTo("INTP");
		assertThat(score.axisScores()).containsExactlyInAnyOrderEntriesOf(Map.of(
			APOLOGY_SINCERITY, 0.5, FAULT_STANDARD, 0.75, PRINCIPLE_RELATION, 0.25, ORDER_OPPORTUNITY, 0.6875));
	}

	@Test
	@DisplayName("테스트 채점: 보통만 고르면 모든 축이 동점 → 관대 글자(INFP), 축 점수는 모두 0.5")
	void scoreTest_allNeutral_isLenientTie() {
		List<Pick> picks = new ArrayList<>();
		for (ValueAxis axis : ValueAxis.values()) {
			for (int i = 0; i < 4; i++) {
				picks.add(new Pick(axis, null, 0));
			}
		}
		Score score = PersonalityScoring.scoreTest(picks);
		assertThat(score.typeCode()).isEqualTo("INFP");
		assertThat(score.axisScores().values()).containsOnly(0.5);
	}

	@Test
	@DisplayName("테스트 채점: 모두 매우 엄격이면 ESTJ, 모두 매우 관대면 INFP (축 점수 0과 1)")
	void scoreTest_extremes() {
		List<Pick> strict = new ArrayList<>();
		List<Pick> lenient = new ArrayList<>();
		for (ValueAxis axis : ValueAxis.values()) {
			for (int i = 0; i < 3; i++) {	// 축당 3문항이어도 같은 식으로 계산한다
				strict.add(new Pick(axis, axis.strictLetter(), 2));
				lenient.add(new Pick(axis, axis.lenientLetter(), 2));
			}
		}
		assertThat(PersonalityScoring.scoreTest(strict).typeCode()).isEqualTo("ESTJ");
		assertThat(PersonalityScoring.scoreTest(strict).axisScores().values()).containsOnly(0.0);
		assertThat(PersonalityScoring.scoreTest(lenient).typeCode()).isEqualTo("INFP");
		assertThat(PersonalityScoring.scoreTest(lenient).axisScores().values()).containsOnly(1.0);
	}

	@Test
	@DisplayName("테스트 채점: 답이 없는 축 · 축에 없는 글자 · 글자와 점수가 어긋난 선택지는 거절한다")
	void scoreTest_rejectsBrokenInput() {
		assertThatThrownBy(() -> PersonalityScoring.scoreTest(List.of(new Pick(APOLOGY_SINCERITY, "E", 2))))
			.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("답이 없는 축");
		List<Pick> wrongLetter = new ArrayList<>();
		for (ValueAxis axis : ValueAxis.values()) {
			wrongLetter.add(new Pick(axis, axis == APOLOGY_SINCERITY ? "S" : null, axis == APOLOGY_SINCERITY ? 2 : 0));
		}
		assertThatThrownBy(() -> PersonalityScoring.scoreTest(wrongLetter))
			.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("축에 없는 글자");
		assertThatThrownBy(() -> new Pick(APOLOGY_SINCERITY, null, 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Pick(APOLOGY_SINCERITY, "E", 0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("체험 하나: 산식 3-5 예시 — 축 점수 1 · 0 · 1 · 0, 종합으로 보면 ISFJ")
	void experience_documentExample() {
		Map<ValueAxis, AxisCount> counts = Map.of(
			APOLOGY_SINCERITY, new AxisCount(1, 0),		// 반성 감경
			FAULT_STANDARD, new AxisCount(0, 2),		// 계획성 · 결과의 중대성 가중
			PRINCIPLE_RELATION, new AxisCount(2, 0),	// 합의 · 피해 회복 감경
			ORDER_OPPORTUNITY, new AxisCount(0, 1));	// 전과 가중
		assertThat(PersonalityScoring.experienceScores(counts)).containsExactlyInAnyOrderEntriesOf(Map.of(
			APOLOGY_SINCERITY, 1.0, FAULT_STANDARD, 0.0, PRINCIPLE_RELATION, 1.0, ORDER_OPPORTUNITY, 0.0));
		assertThat(PersonalityScoring.overall(List.of(counts))).get().extracting(Score::typeCode).isEqualTo("ISFJ");
	}

	@Test
	@DisplayName("체험 하나: 고른 요소가 없는 축은 null (자료 없음)")
	void experience_noData_isNull() {
		assertThat(PersonalityScoring.experienceScore(AxisCount.EMPTY)).isNull();
		assertThat(PersonalityScoring.experienceScores(Map.of(APOLOGY_SINCERITY, new AxisCount(1, 1))))
			.containsEntry(APOLOGY_SINCERITY, 0.5).containsEntry(FAULT_STANDARD, null).hasSize(4);
	}

	@Test
	@DisplayName("종합: 산식 4장 예시 — 평균 0.75 · 0.11 · 0.83 · 0, 자료 없는 체험은 그 축에서 빼고 ISFJ")
	void overall_documentExample() {
		Score score = PersonalityScoring.overall(List.of(
			Map.of(APOLOGY_SINCERITY, new AxisCount(1, 0), FAULT_STANDARD, new AxisCount(0, 1),
				PRINCIPLE_RELATION, new AxisCount(1, 0), ORDER_OPPORTUNITY, new AxisCount(0, 1)),
			Map.of(APOLOGY_SINCERITY, new AxisCount(1, 1), FAULT_STANDARD, new AxisCount(1, 2),
				PRINCIPLE_RELATION, new AxisCount(2, 0)),
			Map.of(FAULT_STANDARD, new AxisCount(0, 3), PRINCIPLE_RELATION, new AxisCount(1, 1)))).orElseThrow();

		assertThat(score.typeCode()).isEqualTo("ISFJ");
		assertThat(score.axisScores().get(APOLOGY_SINCERITY)).isEqualTo(0.75);
		assertThat(score.axisScores().get(FAULT_STANDARD)).isCloseTo(1.0 / 9, within(1e-9));
		assertThat(score.axisScores().get(PRINCIPLE_RELATION)).isCloseTo(5.0 / 6, within(1e-9));
		assertThat(score.axisScores().get(ORDER_OPPORTUNITY)).isEqualTo(0.0);
	}

	@Test
	@DisplayName("종합: 체험 간 정확한 동점(1/3 · 2/3 평균 = 0.5)은 분수로 판정해 관대 글자")
	void overall_exactTie_isLenient() {
		Map<ValueAxis, AxisCount> rest = Map.of(FAULT_STANDARD, new AxisCount(0, 1),
			PRINCIPLE_RELATION, new AxisCount(0, 1), ORDER_OPPORTUNITY, new AxisCount(0, 1));
		Map<ValueAxis, AxisCount> first = new HashMap<>(rest);
		first.put(APOLOGY_SINCERITY, new AxisCount(1, 2));	// 1/3
		Map<ValueAxis, AxisCount> second = new HashMap<>(rest);
		second.put(APOLOGY_SINCERITY, new AxisCount(2, 1));	// 2/3
		assertThat(PersonalityScoring.overall(List.of(first, second))).get()
			.extracting(Score::typeCode).isEqualTo("ISTJ");
	}

	@Test
	@DisplayName("종합: 한 축이라도 자료가 없으면 코드 없이 축 점수만, 모두 없거나 체험이 없으면 종합 없음")
	void overall_missingAxes() {
		Score partial = PersonalityScoring.overall(List.of(Map.of(APOLOGY_SINCERITY, new AxisCount(2, 0)))).orElseThrow();
		assertThat(partial.typeCode()).isNull();
		assertThat(partial.axisScores()).containsEntry(APOLOGY_SINCERITY, 1.0).containsEntry(ORDER_OPPORTUNITY, null);

		assertThat(PersonalityScoring.overall(List.of(Map.of()))).isEmpty();
		assertThat(PersonalityScoring.overall(List.of())).isEmpty();
	}
}
