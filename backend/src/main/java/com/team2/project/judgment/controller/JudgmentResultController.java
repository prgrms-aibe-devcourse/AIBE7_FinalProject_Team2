package com.team2.project.judgment.controller;

import com.team2.project.common.web.Requester;
import com.team2.project.judgment.dto.AiJudgmentResponse;
import com.team2.project.judgment.dto.ComparisonResponse;
import com.team2.project.judgment.dto.CourtJudgmentResponse;
import com.team2.project.judgment.dto.RevealResponse;
import com.team2.project.judgment.service.ComparisonService;
import com.team2.project.judgment.service.JudgmentResultService;
import com.team2.project.judgment.service.RevealService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결과 공개 · 조회 (API 10 ~ 14, S-06 · S-07 · S-08)
 * 상태를 바꾸는 공개는 POST, 결과를 읽는 조회는 GET으로 나눈다. 새로고침하면 조회만 다시 부르면 된다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cases/{caseId}/experience")
@RequiredArgsConstructor
public class JudgmentResultController {

	private final JudgmentResultService judgmentResultService;
	private final RevealService revealService;
	private final ComparisonService comparisonService;

	/** API 10. AI 판결 (S-06) */
	@GetMapping("/judgments/ai")
	public AiJudgmentResponse getAiJudgment(@PathVariable Long caseId, Requester requester) {
		return judgmentResultService.getAiJudgment(caseId, requester.anonymousId());
	}

	/** API 11. 실제 판결 공개 (본문 없음) */
	@PostMapping("/court-reveal")
	public RevealResponse revealCourt(@PathVariable Long caseId, Requester requester) {
		return revealService.revealCourt(caseId, requester.anonymousId());
	}

	/** API 12. 실제 판결 (S-07) */
	@GetMapping("/judgments/court")
	public CourtJudgmentResponse getCourtJudgment(@PathVariable Long caseId, Requester requester) {
		return judgmentResultService.getCourtJudgment(caseId, requester.anonymousId());
	}

	/** API 13. 비교 공개 (본문 없음) */
	@PostMapping("/comparison-reveal")
	public RevealResponse revealComparison(@PathVariable Long caseId, Requester requester) {
		return revealService.revealComparison(caseId, requester.anonymousId());
	}

	/** API 14. 세 판결 비교 (S-08) */
	@GetMapping("/comparison")
	public ComparisonResponse getComparison(@PathVariable Long caseId, Requester requester) {
		return comparisonService.getComparison(caseId, requester.anonymousId());
	}
}
