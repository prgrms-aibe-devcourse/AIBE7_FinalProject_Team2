package com.team2.project.level.controller;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.common.web.Requester;
import com.team2.project.level.dto.JobRequest;
import com.team2.project.level.dto.LevelStatus;
import com.team2.project.level.dto.ShareRewardRequest;
import com.team2.project.level.dto.ShareRewardResponse;
import com.team2.project.level.service.LevelService;
import com.team2.project.level.service.LevelService.SelectResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 레벨 · 직업 API (BE-106, API 명세 L-1 ~ L-3) */
@RestController
@RequestMapping("/api/v1/level")
@RequiredArgsConstructor
public class LevelController {

	private final LevelService levelService;
	private final AnonymousIdCookie anonymousIdCookie;

	/** L-1 내 레벨 · 직업 · 직업 목록. 쿠키가 없어도 200 (Lv1 · 무직) */
	@GetMapping
	public LevelStatus get(Requester requester) {
		return levelService.view(requester.anonymousId());
	}

	/** L-2 카드에 보여 줄 직업 선택. 열리지 않은 직업은 409 JOB_LOCKED. 쿠키가 없었으면 Set-Cookie로 익명 ID 발급 */
	@PutMapping("/job")
	public LevelStatus selectJob(@RequestBody JobRequest body, Requester requester, HttpServletResponse response) {
		if (body == null || body.job() == null) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "job이 필요합니다.");
		}
		SelectResult result = levelService.selectJob(requester.anonymousId(), body.job());
		if (result.issuedCookieId() != null) {
			anonymousIdCookie.write(response, result.issuedCookieId());
		}
		return result.level();
	}

	/** L-3 SNS 공유 보상. 완료한 사건만, 사건당 1회 · 하루 1회. 받은 XP가 0이어도 200 */
	@PostMapping("/share-reward")
	public ShareRewardResponse shareReward(@RequestBody ShareRewardRequest body, Requester requester) {
		if (body == null || body.caseId() == null) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "caseId가 필요합니다.");
		}
		var cookie = requester.anonymousId();
		int awarded = levelService.rewardShare(cookie, body.caseId());
		return new ShareRewardResponse(awarded, levelService.view(cookie));
	}
}
