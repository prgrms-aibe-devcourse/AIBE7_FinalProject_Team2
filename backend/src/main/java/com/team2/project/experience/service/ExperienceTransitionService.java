package com.team2.project.experience.service;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import com.team2.project.experience.repository.ExperienceRepository;
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
 * 3. 0건이면 다른 요청이 먼저 처리한 것이다. 그때의 현재 상태로 INVALID_STATE를 응답한다 (공개 요청은 성공으로 본다)
 *
 * 사용 규칙: 같은 트랜잭션에서 판단 저장(Judgment 등)을 먼저 하고, 이 메서드를 마지막에 부른다.
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
	 * 한 번만 성공해야 하는 상태 이동 (사전 판단 제출, 섹션 확인, 판결 확정)
	 * 다른 요청이 먼저 바꿨으면 INVALID_STATE (currentStatus 포함)
	 */
	@Transactional
	public void apply(Experience experience, Consumer<Experience> change) {
		applyChange(experience, change, false);
	}

	/**
	 * 다시 불러도 성공하는 상태 이동 (실제 판결 공개, 비교 공개 — API 명세 2장 "공개는 멱등")
	 * @return 이번 요청이 상태를 바꿨으면 true, 이미 그 상태 이상이었으면 false
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
		if (experience.getStatus() == beforeStatus && experience.getLastReviewedStep() == beforeStep) {
			return false;	// 바뀐 것이 없음 (이미 공개됨, 이미 확인한 섹션 등)
		}

		int updated = experienceRepository.updateStateIfUnchanged(
			experience.getId(), beforeStatus, beforeStep,
			experience.getStatus(), experience.getLastReviewedStep(),
			experience.getPreJudgedAt(), experience.getReviewedAt(), experience.getVerdictConfirmedAt(),
			experience.getAiRevealedAt(), experience.getCompletedAt(), clock.instant());
		if (updated == 1) {
			return true;
		}

		// 다른 요청이 먼저 처리함
		ExperienceStatus current = experienceRepository.findStatusById(experience.getId())
			.orElseThrow(() -> new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND));
		if (idempotent && current.isAtLeast(experience.getStatus())) {
			return false;
		}
		throw new InvalidExperienceStateException(current, "다른 요청이 먼저 처리되었습니다.");
	}
}
