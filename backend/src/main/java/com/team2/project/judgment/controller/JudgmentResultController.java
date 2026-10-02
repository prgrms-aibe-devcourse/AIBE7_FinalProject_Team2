package com.team2.project.judgment.controller;

import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.judgment.dto.AiJudgmentResponse;
import com.team2.project.judgment.dto.CourtJudgmentResponse;
import com.team2.project.judgment.dto.RevealResponse;
import com.team2.project.judgment.service.JudgmentResultService;
import com.team2.project.judgment.service.RevealService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결과 공개 · 조회 (API 10 ~ 13, S-07 · S-08)
 * 상태를 바꾸는 공개는 POST, 결과를 읽는 조회는 GET으로 나눈다. 새로고침하면 조회만 다시 부르면 된다.
 */
@RestController
@RequestMapping("/api/v1/cases/{caseId}/experience")
@RequiredArgsConstructor
public class JudgmentResultController {

	private final JudgmentResultService judgmentResultService;
	private final RevealService revealService;
	private final AnonymousIdCookie anonymousIdCookie;

	/** API 10. AI 판결 (S-07) */
	@GetMapping("/judgments/ai")
	public AiJudgmentResponse getAiJudgment(@PathVariable Long caseId, HttpServletRequest request) {
		return judgmentResultService.getAiJudgment(caseId, anonymousIdCookie.read(request));
	}

	/** API 11. 실제 판결 공개 (본문 없음) */
	@PostMapping("/court-reveal")
	public RevealResponse revealCourt(@PathVariable Long caseId, HttpServletRequest request) {
		return revealService.revealCourt(caseId, anonymousIdCookie.read(request));
	}

	/** API 12. 실제 판결 (S-08) */
	@GetMapping("/judgments/court")
	public CourtJudgmentResponse getCourtJudgment(@PathVariable Long caseId, HttpServletRequest request) {
		return judgmentResultService.getCourtJudgment(caseId, anonymousIdCookie.read(request));
	}

	/** API 13. 비교 공개 (본문 없음) */
	@PostMapping("/comparison-reveal")
	public RevealResponse revealComparison(@PathVariable Long caseId, HttpServletRequest request) {
		return revealService.revealComparison(caseId, anonymousIdCookie.read(request));
	}
}
