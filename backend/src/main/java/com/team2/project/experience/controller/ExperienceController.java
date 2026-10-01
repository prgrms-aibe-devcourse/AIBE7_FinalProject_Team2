package com.team2.project.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.experience.dto.ExperienceResponse;
import com.team2.project.experience.service.ExperienceService;
import com.team2.project.experience.service.ExperienceService.StartResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/experience")
@RequiredArgsConstructor
public class ExperienceController {

	private final ExperienceService experienceService;
	private final AnonymousIdCookie anonymousIdCookie;

	/** API 2. 체험 시작 — 새로 만들면 201, 이미 있으면 200 */
	@PostMapping
	public ResponseEntity<ExperienceResponse> start(@PathVariable Long caseId, HttpServletRequest request,
			HttpServletResponse response) {
		StartResult result = experienceService.start(caseId, anonymousIdCookie.read(request));
		if (result.issuedCookieId() != null) {
			anonymousIdCookie.write(response, result.issuedCookieId());
		}
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.experience());
	}

	/** API 3. 내 체험 상태 */
	@GetMapping
	public ExperienceResponse getMyExperience(@PathVariable Long caseId, HttpServletRequest request) {
		return experienceService.getMyExperience(caseId, anonymousIdCookie.read(request));
	}
}
