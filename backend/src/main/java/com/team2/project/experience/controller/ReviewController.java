package com.team2.project.experience.controller;

import com.team2.project.common.web.Requester;
import com.team2.project.experience.dto.ReviewResponse;
import com.team2.project.experience.dto.ReviewStepRequest;
import com.team2.project.experience.dto.ReviewStepResponse;
import com.team2.project.experience.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/cases/{caseId}/experience")
@RequiredArgsConstructor
public class ReviewController {

	private final ReviewService reviewService;

	/** API 6. 사건 정보 (S-04) */
	@GetMapping("/review")
	public ReviewResponse getReview(@PathVariable Long caseId, Requester requester) {
		return reviewService.getReview(caseId, requester.anonymousId());
	}

	/** API 7. 섹션 확인 기록 */
	@PostMapping("/review-steps")
	public ReviewStepResponse confirmStep(@PathVariable Long caseId, @Valid @RequestBody ReviewStepRequest body,
		Requester requester) {
		return reviewService.confirmStep(caseId, requester.anonymousId(), body);
	}
}
