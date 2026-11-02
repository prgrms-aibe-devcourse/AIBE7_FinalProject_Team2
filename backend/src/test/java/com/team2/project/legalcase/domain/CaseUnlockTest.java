package com.team2.project.legalcase.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.team2.project.legalcase.domain.CaseUnlock.Candidate;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 사건 순차 해금 규칙 (BE-146) */
class CaseUnlockTest {

	/** 순서 값 없이 id 1 ~ count인 사건 */
	private static List<Candidate> cases(int count) {
		return LongStream.rangeClosed(1, count).mapToObj(id -> new Candidate(id, null)).toList();
	}

	@ParameterizedTest(name = "완료 {0}건 → 열린 사건 {1}건, 다음 단계 {2}")
	@CsvSource({"0, 3, 1", "1, 6, 3", "2, 6, 3", "3, 12, 5", "4, 12, 5", "5, 2147483647, ", "20, 2147483647, "})
	@DisplayName("단계: 0→3 · 1→6 · 3→12 · 5→전체")
	void steps(long completed, int open, Integer next) {
		assertThat(CaseUnlock.openCount(completed)).isEqualTo(open);
		assertThat(CaseUnlock.nextStepAt(completed)).isEqualTo(next);
	}

	@Test
	@DisplayName("처음에는 앞의 3건만 열리고 나머지는 잠긴다")
	void firstThreeOpen() {
		assertThat(CaseUnlock.locked(cases(5), Set.of(), 0)).containsExactlyInAnyOrder(4L, 5L);
	}

	@Test
	@DisplayName("마지막 단계면 아무것도 잠기지 않는다")
	void allOpenAtLastStep() {
		assertThat(CaseUnlock.locked(cases(30), Set.of(), 5)).isEmpty();
	}

	@Test
	@DisplayName("순서는 unlock_order 오름차순, 비어 있으면 그 뒤에 id 순")
	void orderedByUnlockOrderThenId() {
		List<Candidate> candidates = List.of(
			new Candidate(1, null), new Candidate(2, null), new Candidate(3, 2), new Candidate(4, 1), new Candidate(5, null));
		// 4(1) → 3(2) → 1 → 2 → 5
		assertThat(CaseUnlock.locked(candidates, Set.of(), 0)).containsExactlyInAnyOrder(2L, 5L);
	}

	@Test
	@DisplayName("이미 체험한 사건은 순서 밖이어도 열려 있고, 다른 사건의 자리를 빼앗지 않는다")
	void touchedStaysOpen() {
		assertThat(CaseUnlock.locked(cases(5), Set.of(5L), 0)).containsExactly(4L);
	}
}
