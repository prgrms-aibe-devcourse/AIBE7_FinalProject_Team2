package com.team2.project.legalcase.domain;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 사건 순차 해금 규칙 (BE-146, docs/decisions/BE-146.md). 완료한 사건 수만큼 단계가 올라 열린 사건 수가 늘어난다.
 *
 * <ul>
 *   <li>순서에 서는 사건은 <b>체험할 수 있는 사건(대본 READY)</b>뿐이다. 준비 중 · 점검 중 사건은 자리를 차지하지 않고 잠기지도 않는다
 *       (화면이 "준비 중" · "점검 중"으로 막는다)</li>
 *   <li>순서는 {@code unlock_order} 오름차순, 비어 있으면 그 뒤에 사건 id 순이다</li>
 *   <li>이미 체험한(진행 중 · 완료 · 가져온) 사건은 순서와 상관없이 늘 열려 있다</li>
 * </ul>
 */
public final class CaseUnlock {

	/** 단계: {완료한 사건 수, 열린 사건 수}. 완료 수가 큰 단계부터 본다. 마지막 단계는 전체다 */
	private static final int[][] STEPS = {{5, Integer.MAX_VALUE}, {3, 12}, {1, 6}, {0, 3}};

	private static final Comparator<Candidate> ORDER = Comparator
		.comparing(Candidate::unlockOrder, Comparator.nullsLast(Comparator.naturalOrder()))
		.thenComparingLong(Candidate::caseId);

	private CaseUnlock() {
	}

	/** 순서에 서는 사건 하나: 체험할 수 있는(대본 READY) 공개 사건 */
	public record Candidate(long caseId, Integer unlockOrder) {
	}

	/** 완료한 사건 수일 때 열린 사건 수 */
	public static int openCount(long completedCases) {
		for (int[] step : STEPS) {
			if (completedCases >= step[0]) {
				return step[1];
			}
		}
		return STEPS[STEPS.length - 1][1];
	}

	/** 다음 단계가 열리는 완료 수. 이미 마지막 단계면 null */
	public static Integer nextStepAt(long completedCases) {
		Integer next = null;
		for (int[] step : STEPS) {
			if (completedCases < step[0]) {
				next = step[0];
			}
		}
		return next;
	}

	/**
	 * 잠긴 사건 id.
	 *
	 * @param candidates 체험할 수 있는 공개 사건(순서 무관)
	 * @param touched 이미 체험한 사건 id(늘 열려 있다)
	 * @param completedCases 완료한 사건 수
	 */
	public static Set<Long> locked(List<Candidate> candidates, Set<Long> touched, long completedCases) {
		int open = openCount(completedCases);
		List<Candidate> ordered = candidates.stream().sorted(ORDER).toList();
		Set<Long> locked = new HashSet<>();
		for (int i = open; i < ordered.size(); i++) {
			long caseId = ordered.get(i).caseId();
			if (!touched.contains(caseId)) {
				locked.add(caseId);
			}
		}
		return locked;
	}
}
