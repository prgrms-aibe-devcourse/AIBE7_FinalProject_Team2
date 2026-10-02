package com.team2.project.judgment.service;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.service.ExperienceTransitionService;
import com.team2.project.experience.service.ExperienceTransitionService.TransitionResult;
import com.team2.project.experience.service.MyExperienceService;
import com.team2.project.judgment.dto.RevealResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결과 공개 (API 11 실제 판결 공개, API 13 비교 공개)
 * 공개 요청은 다시 보내도 결과가 같다. 이미 공개된 상태면 상태를 그대로 두고 현재 상태를 돌려준다
 * (API 명세 2장 · 시퀀스 7장). 결과 화면 사이를 오가거나 새로고침해도 거절되지 않게 하기 위해서다.
 * 응답은 엔티티가 아니라 TransitionResult의 상태로 만든다 — 동시 요청이 먼저 진행시킨 경우
 * 엔티티에는 이번 요청이 만들려던 값이 들어 있을 뿐 실제 현재 상태가 아니다 (ExperienceTransitionService).
 */
@Service
@RequiredArgsConstructor
public class RevealService {

	private final MyExperienceService myExperienceService;
	private final ExperienceTransitionService transitionService;
	private final Clock clock;

	/** API 11. VERDICT_CONFIRMED면 AI_REVEALED로. 이미 공개했으면 그대로 */
	@Transactional
	public RevealResponse revealCourt(Long caseId, Optional<UUID> anonymousId) {
		Experience experience = myExperienceService.getMyExperienceAtLeast(caseId, anonymousId,
			ExperienceStatus.VERDICT_CONFIRMED);
		Instant now = clock.instant();
		// (확장) 처음 공개할 때 comparison_analysis를 PENDING으로 만들고 AI 비교 분석 생성을 시작한다 (시퀀스 8장).
		// MVP는 규칙 기반 문장만 쓰므로 생성하지 않는다.
		TransitionResult result = transitionService.applyIdempotent(experience, target -> target.revealCourt(now));
		return new RevealResponse(result.status());
	}

	/** API 13. AI_REVEALED면 COMPLETED로. 이미 완료했으면 그대로 */
	@Transactional
	public RevealResponse revealComparison(Long caseId, Optional<UUID> anonymousId) {
		Experience experience = myExperienceService.getMyExperienceAtLeast(caseId, anonymousId,
			ExperienceStatus.AI_REVEALED);
		Instant now = clock.instant();
		TransitionResult result = transitionService.applyIdempotent(experience, target -> target.revealComparison(now));
		return new RevealResponse(result.status());
	}
}
