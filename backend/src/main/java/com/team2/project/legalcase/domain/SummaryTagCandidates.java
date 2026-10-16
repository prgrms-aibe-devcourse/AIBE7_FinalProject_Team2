package com.team2.project.legalcase.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 판단 요소 요약어 후보 (factor.summary_tag_candidates, jsonb, BE-80)
 * 파이프라인 extract가 요약어를 공개 기준 목록과 임베딩으로 비교한 결과다(BE-53, tools/ai-judgment/summary_tags.py).
 * 요약어는 바꾸지 않고 관리자에게 제안만 한다.
 *
 * @param candidates   비슷한 기준 요약어 (점수 높은 순)
 * @param inVocabulary 요약어가 기준 목록에 글자 그대로 있음
 * @param ambiguous    1 · 2순위 점수 차가 작아 제안하지 않음
 * @param suggested    제안하는 기준 요약어 (없으면 null)
 *
 * 모르는 키는 무시한다(#166 리뷰): 파이프라인이 나중에 키를 더하거나, 자동 배포가 이전 앱으로 되돌린 뒤 새 키가 든 행을 읽어도
 * 요소 조회(A-6 · 사전 판단 · 판결 · 비교)가 실패하지 않게 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SummaryTagCandidates(List<Candidate> candidates, boolean inVocabulary, boolean ambiguous, String suggested) {

	/** 기준 요약어 하나와 유사도 (cosine, 소수 넷째 자리) */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Candidate(String tag, double score) {
	}
}
