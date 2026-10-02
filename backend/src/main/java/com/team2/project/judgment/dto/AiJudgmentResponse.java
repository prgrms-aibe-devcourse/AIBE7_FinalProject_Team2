package com.team2.project.judgment.dto;

import com.team2.project.judgment.domain.Judgment;
import com.team2.project.legalcase.domain.PenaltyType;
import java.util.List;

/**
 * API 10. AI 판결 (S-07)
 * 검수해 공개한 AI 판결을 DB에서 읽기만 한다. AI를 호출하지 않는다 (시퀀스 6장).
 */
public record AiJudgmentResponse(
	JudgmentView judgment,
	JudgmentView myJudgment,
	DiffFromMine diffFromMine,
	List<String> references
) {
	/**
	 * 내 판결과의 차이 (MVP는 숫자 차이만).
	 * 형벌 종류는 최종 선고 형벌(reducedTo가 있으면 그 값)로 비교한다 (API 명세 v0.4).
	 * 종류가 다르면 숫자 차이는 모두 null이다. 사형 · 무기는 형량 값이 없어 같은 종류여도 null이다.
	 */
	public record DiffFromMine(boolean samePenaltyType, Integer prisonMonthsDiff, Long fineAmountDiff) {

		/** target(AI · 재판부) 판결에서 내 판결을 뺀 값. 음수면 내 판결보다 가볍다 */
		public static DiffFromMine between(Judgment target, Judgment mine) {
			PenaltyType finalType = target.getFinalPenaltyType();
			if (finalType != mine.getFinalPenaltyType()) {
				return new DiffFromMine(false, null, null);
			}
			// 형벌 종류가 같아도 그 종류에 해당하는 숫자만 내려간다 (징역은 개월, 벌금은 원, 사형 · 무기는 없음)
			return switch (finalType) {
				case PRISON -> new DiffFromMine(true, diff(target.getPrisonMonths(), mine.getPrisonMonths()), null);
				case FINE -> new DiffFromMine(true, null, diff(target.getFineAmount(), mine.getFineAmount()));
				case DEATH, LIFE -> new DiffFromMine(true, null, null);
			};
		}

		private static Integer diff(Integer target, Integer mine) {
			return target == null || mine == null ? null : target - mine;
		}

		private static Long diff(Long target, Long mine) {
			return target == null || mine == null ? null : target - mine;
		}
	}
}
