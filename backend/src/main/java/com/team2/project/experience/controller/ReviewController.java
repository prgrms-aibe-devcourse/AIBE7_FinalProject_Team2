package com.team2.project.experience.controller;

import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.experience.dto.ReviewResponse;
import com.team2.project.experience.dto.ReviewStepRequest;
import com.team2.project.experience.dto.ReviewStepResponse;
import com.team2.project.experience.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/experience")
@RequiredArgsConstructor
public class ReviewController {

	private final ReviewService reviewService;
	private final AnonymousIdCookie anonymousIdCookie;

	/** API 6. 사건 정보 (S-04 · S-05) */
	@GetMapping("/review")
	public ReviewResponse getReview(@PathVariable Long caseId, HttpServletRequest request) {
		return reviewService.getReview(caseId, anonymousIdCookie.read(request));
	}

	/** API 7. 섹션 확인 기록 */
	@PostMapping("/review-steps")
	public ReviewStepResponse confirmStep(@PathVariable Long caseId, @Valid @RequestBody ReviewStepRequest body,
		HttpServletRequest request) {
		return reviewService.confirmStep(caseId, anonymousIdCookie.read(request), body);
	}
}
