package com.team2.project.judgment.domain;

import com.team2.project.legalcase.domain.PenaltyRangeText;
import com.team2.project.legalcase.domain.PenaltyType;

/** 최종 판결을 사람이 읽는 한 줄 문구로 (API 14 preToFinal.finalJudgmentText) */
public final class FinalJudgmentText {

	private FinalJudgmentText() {
	}

	public static String of(Judgment judgment) {
		PenaltyType finalType = judgment.getFinalPenaltyType();
		String base = switch (finalType) {
			case DEATH -> "사형";
			case LIFE -> "무기징역";
			case PRISON -> "징역 " + PenaltyRangeText.formatMonths(judgment.getPrisonMonths());
			case FINE -> "벌금 " + PenaltyRangeText.formatMoney(judgment.getFineAmount());
		};
		if (judgment.isSuspended()) {
			return base + "에 집행유예 " + PenaltyRangeText.formatMonths(judgment.getSuspensionMonths());
		}
		return base;
	}
}
