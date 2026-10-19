package com.team2.project.legalcase.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * 가치관 축 자동 분류 투표 기록 (factor.value_axis_votes, jsonb)
 * 예: { "runs": 5, "counts": { "FAULT_STANDARD": 3, "PRINCIPLE_RELATION": 2 }, "needsReview": false, "requestedRuns": 5 }
 *
 * @param runs          집계에 들어간 분류 횟수 (유효 응답 수)
 * @param counts        축별 표 수. 표를 받은 축만 담고, 어느 축에도 맞지 않음(NULL) 표는 "NONE" 키로 센다
 * @param needsReview   최다표가 요청 횟수의 과반이 아니면(동률 · 유효 응답 부족 포함) true (관리자 확인 필요)
 * @param requestedRuns 요청한 분류 횟수. 있으면 needsReview를 다시 계산할 수 있다 (예전 기록 · 사람 초안은 null)
 *
 * 모르는 키는 무시한다(BE-81, SummaryTagCandidates와 같음): 파이프라인이 키를 더하거나 자동 배포가 이전 앱으로 되돌린 뒤
 * 새 키가 든 행을 읽어도 요소 조회(A-6 · 사전 판단 · 판결 · 비교)가 실패하지 않게 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ValueAxisVotes(int runs, Map<String, Integer> counts, boolean needsReview, Integer requestedRuns) {
}
