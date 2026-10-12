package com.team2.project.judgment.dto;

import java.util.List;

/**
 * API 12. 실제 판결 (S-08)
 * 실제 판결과 함께 내 판결 · AI 판결을 돌려준다. 세 판결을 한 화면에서 비교할 수 있게 하기 위해서다.
 */
public record CourtJudgmentResponse(
	JudgmentView judgment,
	JudgmentView myJudgment,
	JudgmentView aiJudgment,
	Source source,
	List<String> deidentifiedItems
) {
	/**
	 * 출처 기관 (확장 REQ-055). case_source 중 최종 확정 판결 행의 source_org만 쓴다.
	 * 사건번호 · 법원명 · 선고일은 절대 넣지 않는다 (FR-5-3).
	 */
	public record Source(String sourceOrg) { }
}
