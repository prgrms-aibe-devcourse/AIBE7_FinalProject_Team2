package com.team2.project.judgment.service;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.service.ExperienceTransitionService;
import com.team2.project.experience.service.ExperienceTransitionService.TransitionResult;
import com.team2.project.experience.service.MyExperienceService;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.SubjectType;
import com.team2.project.judgment.dto.RevealResponse;
import com.team2.project.judgment.repository.JudgmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
	private final JudgmentRepository judgmentRepository;
	private final Clock clock;

	/** API 11. VERDICT_CONFIRMED면 AI_REVEALED로. 이미 공개했으면 그대로 */
	@Transactional
	public RevealResponse revealCourt(Long caseId, Optional<UUID> anonymousId) {
		Experience experience = myExperienceService.getMyExperienceAtLeast(caseId, anonymousId,
			ExperienceStatus.VERDICT_CONFIRMED);
		requirePublishedJudgments(caseId);
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
		requirePublishedJudgments(caseId);
		Instant now = clock.instant();
		TransitionResult result = transitionService.applyIdempotent(experience, target -> target.revealComparison(now));
		return new RevealResponse(result.status());
	}

	/**
	 * 다음 화면이 읽을 판결이 실제로 있는지 상태를 옮기기 전에 확인한다 (BE-10 리뷰).
	 * 확인하지 않으면, 판결이 등록되지 않은 사건에서 상태만 AI_REVEALED · COMPLETED로 넘어가고
	 * 그 뒤 조회(API 12 · 14)가 매번 500이 되어 되돌릴 방법 없이 결과 화면에 갇힌다.
	 * 공개 사건에 검수한 판결이 반드시 있다는 불변식은 사건을 공개하는 쪽에서 보장하는 것이 맞지만,
	 * MVP는 팀이 SQL로 등록하므로 강제할 지점이 없어 여기서 한 번 더 막는다 (BE-17 후속).
	 */
	private void requirePublishedJudgments(Long caseId) {
		Set<SubjectType> published = judgmentRepository.findPublishedJudgments(caseId).stream()
			.map(Judgment::getSubjectType)
			.collect(Collectors.toSet());
		for (SubjectType required : List.of(SubjectType.AI, SubjectType.COURT)) {
			if (!published.contains(required)) {
				throw JudgmentResultService.missingJudgment(caseId, required);
			}
		}
	}
}
