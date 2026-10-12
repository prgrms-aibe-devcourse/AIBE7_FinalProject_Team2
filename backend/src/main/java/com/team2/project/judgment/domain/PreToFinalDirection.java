package com.team2.project.judgment.domain;

import com.team2.project.legalcase.domain.PenaltyType;
import com.team2.project.legalcase.domain.RangeKind;
import com.team2.project.legalcase.domain.SentenceRangeOption;

/**
 * 사전 판단 구간과 최종 판결 비교 (API 14 preToFinal.direction, API 명세 "preToFinal.direction")
 * 형벌 종류의 무게는 FINE &lt; SUSPENDED &lt; PRISON &lt; LIFE &lt; DEATH 순이며, 이는 RangeKind의 선언 순서와 같다.
 * 최종 판결은 reducedTo가 있으면 그 값(최종 선고 형벌)으로 비교한다(ERD sentence_range_option 무겁기 순서와 같다).
 * 무죄는 MVP에서 뺐으므로 비교 대상이 아니다(API 명세 v0.3).
 */
public enum PreToFinalDirection {
	HEAVIER,	// 최종 판결이 구간보다 무거움
	SAME,		// 최종 판결이 구간 안
	LIGHTER;	// 최종 판결이 구간보다 가벼움

	public static PreToFinalDirection of(SentenceRangeOption preRange, Judgment finalJudgment) {
		RangeKind preKind = preRange.getKind();
		RangeKind finalKind = finalKind(finalJudgment);
		int comparison = finalKind.compareTo(preKind);
		if (comparison > 0) {
			return HEAVIER;
		}
		if (comparison < 0) {
			return LIGHTER;
		}
		if (finalKind == RangeKind.PRISON) {
			return withinPrisonRange(preRange, finalJudgment.getPrisonMonths());
		}
		// PRISON이 아닌 같은 종류(FINE · SUSPENDED · LIFE · DEATH)는 구간이 더 세분되지 않는다
		return SAME;
	}

	/** 구간 하한 · 상한이 없으면(NULL) 그쪽은 제한이 없는 것으로 본다 */
	private static PreToFinalDirection withinPrisonRange(SentenceRangeOption preRange, int months) {
		Integer min = preRange.getMinMonths();
		Integer max = preRange.getMaxMonths();
		if (min != null && months < min) {
			return LIGHTER;
		}
		if (max != null && months >= max) {
			return HEAVIER;
		}
		return SAME;
	}

	/**
	 * 최종 선고 형벌(reducedTo 우선) + 집행유예 여부를 구간 종류로 바꾼다.
	 * 벌금 집행유예는 MVP 대표 사건(살인)에 없는 조합이라 FINE으로만 다룬다.
	 */
	private static RangeKind finalKind(Judgment judgment) {
		PenaltyType type = judgment.getFinalPenaltyType();
		return switch (type) {
			case FINE -> RangeKind.FINE;
			case PRISON -> judgment.isSuspended() ? RangeKind.SUSPENDED : RangeKind.PRISON;
			case LIFE -> RangeKind.LIFE;
			case DEATH -> RangeKind.DEATH;
		};
	}
}
