package com.team2.project.judgment.domain;

import com.team2.project.legalcase.domain.PenaltyType;

/**
 * 두 판결의 형벌 차이 (API 10 diffFromMine, 이후 API 14에서도 쓴다)
 *
 * - 형벌 종류는 **최종 선고 형벌**(reducedTo가 있으면 그 값, API 명세 v0.4)로 비교한다.
 * - **집행유예는 실형과 다른 단계로 본다** (API 14 형벌 무게 순서 FINE < SUSPENDED < PRISON < LIFE < DEATH).
 *   징역 2년 집행유예 3년과 징역 2년 실형은 개월 수가 같아도 "같은 판결"이 아니기 때문이다 (BE-10 리뷰).
 * - 종류가 같을 때도 그 종류에 해당하는 값만 내려간다 (징역은 개월, 벌금은 원, 사형 · 무기는 없음).
 * - 둘 다 집행유예면 유예 기간도 비교한다. 징역 2년 집행유예 1년과 징역 2년 집행유예 3년은
 *   징역 개월 수가 같아도 같은 판결이 아니다 (BE-10 리뷰).
 */
public record PenaltyDifference(boolean samePenaltyType, Integer prisonMonthsDiff, Long fineAmountDiff,
	Integer suspensionMonthsDiff) {

	/** 형벌 종류가 달라 숫자로 비교할 수 없음 */
	private static final PenaltyDifference DIFFERENT_TYPE = new PenaltyDifference(false, null, null, null);

	/** target(AI · 재판부) 판결에서 내 판결을 뺀 값. 음수면 내 판결보다 가볍다 */
	public static PenaltyDifference between(Judgment target, Judgment mine) {
		PenaltyType finalType = target.getFinalPenaltyType();
		if (finalType != mine.getFinalPenaltyType() || target.isSuspended() != mine.isSuspended()) {
			return DIFFERENT_TYPE;
		}
		// 둘 다 집행유예가 아니면 양쪽 모두 NULL이라 자연히 null이 된다
		Integer suspensionDiff = diff(target.getSuspensionMonths(), mine.getSuspensionMonths());
		return switch (finalType) {
			case PRISON -> new PenaltyDifference(true,
				diff(target.getPrisonMonths(), mine.getPrisonMonths()), null, suspensionDiff);
			case FINE -> new PenaltyDifference(true,
				null, diff(target.getFineAmount(), mine.getFineAmount()), suspensionDiff);
			case DEATH, LIFE -> new PenaltyDifference(true, null, null, null);
		};
	}

	private static Integer diff(Integer target, Integer mine) {
		return target == null || mine == null ? null : target - mine;
	}

	private static Long diff(Long target, Long mine) {
		return target == null || mine == null ? null : target - mine;
	}
}
