package com.team2.project.experience.service;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.experience.repository.ExperienceState;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체험 상태를 바꾸는 요청의 공통 처리 (IA 9장, API 명세 API 5 · 7 · 9 · 11 · 13)
 *
 * 1. 엔티티 메서드(markPreJudged 등)로 규칙을 검사하고 새 상태를 만든다 (규칙은 엔티티 한 곳에만)
 * 2. 조건부 갱신으로 DB에 반영한다 — 읽은 뒤 다른 요청이 상태를 바꿨으면 0건이 되어 반영하지 않는다
 * 3. 0건이면 다른 요청이 먼저 처리한 것이다. 그때의 현재 상태로 INVALID_STATE를 응답한다 (멱등 요청은 성공으로 본다)
 *
 * 사용 규칙: 같은 트랜잭션에서 이 메서드(상태 전이)를 먼저 부르고, 판단 저장(Judgment 등)은 그 다음에 한다.
 * 반대 순서(판단 먼저 저장)로 하면, 동시 요청의 패자가 조건부 갱신에 닿기 전에 판단 테이블의 유니크 제약
 * 위반으로 먼저 실패해 currentStatus 없는 일반 INVALID_STATE 응답이 나간다 (리뷰 반영).
 * 넘긴 체험 엔티티는 영속성 컨텍스트에서 분리되므로, 호출 뒤에는 그 체험의 지연 로딩(getLegalCase() 등)을 쓰지 않는다.
 * (다른 엔티티는 그대로 관리된다)
 */
@Service
@RequiredArgsConstructor
public class ExperienceTransitionService {

	private final ExperienceRepository experienceRepository;

	private final EntityManager entityManager;

	private final Clock clock;

	/**
	 * 한 번만 성공해야 하는 상태 이동 (사전 판단 제출, 판결 확정)
	 * 다른 요청이 먼저 바꿨으면 INVALID_STATE (currentStatus 포함)
	 */
	@Transactional
	public void apply(Experience experience, Consumer<Experience> change) {
		applyChange(experience, change, false);
	}

	/**
	 * 다시 불러도 성공하는 상태 이동. 두 가지를 모두 포함한다.
	 * - 공개(실제 판결, 비교 공개): 상태만 보면 된다 (API 명세 2장 "공개는 멱등")
	 * - 섹션 확인(API 7): 상태가 같아도(REVIEWING 안에서 2 → 3) lastReviewedStep까지 비교해야
	 *   "이미 그 섹션 이상"을 올바르게 판정할 수 있다 (리뷰 반영)
	 * 다른 요청이 이미 그 지점(상태, 그리고 상태가 같다면 섹션까지) 이상으로 진행시켰으면 성공으로 본다.
	 * @return 이번 요청이 상태를 바꿨으면 true, 이미 그 지점 이상이었으면 false
	 */
	@Transactional
	public boolean applyIdempotent(Experience experience, Consumer<Experience> change) {
		return applyChange(experience, change, true);
	}

	private boolean applyChange(Experience experience, Consumer<Experience> change, boolean idempotent) {
		ExperienceStatus beforeStatus = experience.getStatus();
		int beforeStep = experience.getLastReviewedStep();

		// 엔티티 변경이 자동 반영(flush)되면 조건 없이 UPDATE가 나가므로, 분리한 뒤 규칙 검사 · 변경만 한다
		entityManager.detach(experience);
		change.accept(experience);
		ExperienceStatus targetStatus = experience.getStatus();
		int targetStep = experience.getLastReviewedStep();
		if (targetStatus == beforeStatus && targetStep == beforeStep) {
			return false;	// 바뀐 것이 없음 (이미 공개됨, 이미 확인한 섹션 등)
		}

		int updated = experienceRepository.updateStateIfUnchanged(
			experience.getId(), beforeStatus, beforeStep,
			targetStatus, targetStep,
			experience.getPreJudgedAt(), experience.getReviewedAt(), experience.getVerdictConfirmedAt(),
			experience.getAiRevealedAt(), experience.getCompletedAt(), clock.instant());
		if (updated == 1) {
			return true;
		}

		// 다른 요청이 먼저 처리함
		ExperienceState current = experienceRepository.findStateById(experience.getId())
			.orElseThrow(() -> new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND));
		boolean alreadyThere = current.status().ordinal() > targetStatus.ordinal()
			|| (current.status() == targetStatus && current.lastReviewedStep() >= targetStep);
		if (idempotent && alreadyThere) {
			return false;
		}
		throw new InvalidExperienceStateException(current.status(), "다른 요청이 먼저 처리되었습니다.");
	}
}
