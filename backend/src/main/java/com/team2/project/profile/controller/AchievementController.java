package com.team2.project.profile.controller;

import com.team2.project.common.web.Requester;
import com.team2.project.profile.dto.AchievementSeenRequest;
import com.team2.project.profile.service.AchievementSeenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 도전과제 "봤음" API (BE-83, API 명세 P-11). 진행도 · 해금은 마이페이지(P-5) achievements에 함께 내려 준다 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AchievementController {

	private final AchievementSeenService achievementSeenService;

	/** P-11 도전과제 "봤음" — 204 (멱등) */
	@PostMapping("/achievements/seen")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markSeen(@Valid @RequestBody AchievementSeenRequest body, Requester requester) {
		achievementSeenService.markSeen(requester.anonymousId(), body.achievementIds());
	}
}
