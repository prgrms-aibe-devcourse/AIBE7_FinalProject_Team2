package com.team2.project.experience.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team2.project.experience.dto.ExperienceResponse;
import com.team2.project.experience.dto.OverviewResponse;
import com.team2.project.experience.dto.PreJudgmentRequest;
import com.team2.project.experience.dto.PreJudgmentResponse;
import com.team2.project.experience.service.ExperienceService;
import com.team2.project.experience.service.ExperienceService.StartResult;
import com.team2.project.experience.service.PreJudgmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/experience")
@RequiredArgsConstructor
public class ExperienceController {

	private final ExperienceService experienceService;
	private final PreJudgmentService preJudgmentService;

	/** API 2. 체험 시작 — 새로 만들면 201, 이미 있으면 200 */
	@PostMapping
	public ResponseEntity<ExperienceResponse> start(
			@PathVariable Long caseId,
			@CookieValue(name = AnonymousIdCookie.NAME, required = false) String anonymousId) {
		StartResult result = experienceService.start(caseId, AnonymousIdCookie.parse(anonymousId));

		ResponseEntity.BodyBuilder response = ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK);
		if (result.issuedCookieId() != null) {
			response.header(HttpHeaders.SET_COOKIE, AnonymousIdCookie.issue(result.issuedCookieId()).toString());
		}
		return response.body(result.experience());
	}

	/** API 3. 내 체험 상태 */
	@GetMapping
	public ExperienceResponse getMyExperience(
			@PathVariable Long caseId,
			@CookieValue(name = AnonymousIdCookie.NAME, required = false) String anonymousId) {
		return experienceService.getMyExperience(caseId, AnonymousIdCookie.parse(anonymousId));
	}

	/** API 4. 사건 개요 · 사전 판단 선택지 */
	@GetMapping("/overview")
	public OverviewResponse getOverview(
			@PathVariable Long caseId,
			@CookieValue(name = AnonymousIdCookie.NAME, required = false) String anonymousId) {
		return preJudgmentService.getOverview(caseId, AnonymousIdCookie.parse(anonymousId));
	}

	/** API 5. 사전 판단 제출 */
	@PostMapping("/pre-judgment")
	public PreJudgmentResponse submitPreJudgment(
			@PathVariable Long caseId,
			@CookieValue(name = AnonymousIdCookie.NAME, required = false) String anonymousId,
			@Valid @RequestBody PreJudgmentRequest request) {
		return preJudgmentService.submit(caseId, AnonymousIdCookie.parse(anonymousId), request);
	}
}
