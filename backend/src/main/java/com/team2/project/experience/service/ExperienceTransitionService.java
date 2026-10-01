package com.team2.project.experience.service;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.exception.UniqueViolations;
import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.experience.repository.ExperienceState;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체험 상태를 바꾸는 요청의 공통 처리 (IA 9장, API 명세 API 5 · 7 · 9 · 11 · 13)
 *
 * 1. 엔티티 메서드(markPreJudged 등)로 규칙을 검사하고 새 상태를 만든다 (규칙은 엔티티 한 곳에만)
 * 2. 조건부 갱신으로 DB에 반영한다 — 읽은 뒤 다른 요청이 상태를 바꿨으면 0건이 되어 반영하지 않는다
 * 3. 0건이면 다른 요청이 먼저 처리한 것이다. 그때의 현재 상태로 INVALID_STATE를 응답한다 (멱등 요청은 성공으로 본다)
 *
 * 판단(Judgment) 등 함께 저장할 데이터가 있으면 {@link #apply(Experience, Runnable, Consumer)}를 쓴다.
 * 저장 순서를 직접 맞출 필요 없이, 이 메서드 하나가 "함께 저장 → 조건부 전이"를 한 트랜잭션으로 묶고
 * 동시 제출로 유니크 제약을 만나도 currentStatus 포함 INVALID_STATE로 통일해서 응답한다 (리뷰 반영 —
 * 같은 패턴을 PR마다 다르게 짜지 않도록 여기 한 곳에 둔다).
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
	 * 한 번만 성공해야 하는 상태 이동 (공개처럼 함께 저장할 데이터가 없는 경우)
	 * 다른 요청이 먼저 바꿨으면 INVALID_STATE (currentStatus 포함)
	 */
	@Transactional
	public void apply(Experience experience, Consumer<Experience> change) {
		applyChange(experience, change, false);
	}

	/**
	 * 한 번만 성공해야 하는 상태 이동이면서, 판단(Judgment) 등 함께 저장할 데이터가 있는 경우
	 * (사전 판단 제출 API 5, 판결 확정 API 9). relatedWrites를 먼저 실행한 뒤 상태를 전이한다.
	 * relatedWrites 저장이 유니크 제약 위반으로 실패하면(동시 중복 제출) 현재 상태를 다시 읽어
	 * INVALID_STATE(currentStatus 포함)로 바꿔 던진다 — 호출 쪽에서 저장 순서를 신경 쓰지 않아도 된다.
	 */
	@Transactional
	public void apply(Experience experience, Runnable relatedWrites, Consumer<Experience> change) {
		try {
			relatedWrites.run();
			// relatedWrites가 기존 엔티티를 고치기만 했다면(예: 다른 엔티티의 필드 변경) 그 UPDATE는
			// 보통 뒤의 조건부 갱신(flushAutomatically) 때야 나간다. 여기서 바로 flush해서, 그 변경이
			// 유니크 제약을 위반하더라도 이 catch에서 받히게 한다 (ID 생성 전략이 IDENTITY가 아닌
			// 엔티티를 저장하는 경우에도 안전).
			entityManager.flush();
		} catch (DataIntegrityViolationException e) {
			throw asCleanConflict(experience.getId(), e);
		}
		applyChange(experience, change, false);
	}

	/**
	 * 다시 불러도 성공하는 상태 이동. 두 가지를 모두 포함한다.
	 * - 공개(실제 판결, 비교 공개): 상태만 보면 된다 (API 명세 2장 "공개는 멱등")
	 * - 섹션 확인(API 7): 상태가 같아도(REVIEWING 안에서 2 → 3) lastReviewedStep까지 비교해야
	 *   "이미 그 섹션 이상"을 올바르게 판정할 수 있다 (리뷰 반영)
	 * 다른 요청이 이미 그 지점(상태, 그리고 상태가 같다면 섹션까지) 이상으로 진행시켰으면 성공으로 본다.
	 * 이미 그 지점이었던 경우, 넘긴 체험 엔티티에는 "이번 요청이 만들려던 값"이 들어 있을 뿐 실제
	 * 현재 값이 아닐 수 있다(예: 동시에 step 3을 확인하는 중 다른 요청이 step 4까지 먼저 끝난 경우).
	 * API 명세 API 7 "현재 상태를 그대로 돌려준다"를 지키려면, 호출 쪽은 TransitionResult의
	 * status · lastReviewedStep으로 응답을 만들어야 한다(엔티티 값을 쓰지 말 것) (리뷰 반영).
	 */
	@Transactional
	public TransitionResult applyIdempotent(Experience experience, Consumer<Experience> change) {
		return applyChange(experience, change, true);
	}

	/** 상태 전이 결과. changed가 false(멱등 성공)일 때 status · lastReviewedStep은 실제 현재 값이다 */
	public record TransitionResult(boolean changed, ExperienceStatus status, int lastReviewedStep) {
	}

	private TransitionResult applyChange(Experience experience, Consumer<Experience> change, boolean idempotent) {
		ExperienceStatus beforeStatus = experience.getStatus();
		int beforeStep = experience.getLastReviewedStep();

		// 엔티티 변경이 자동 반영(flush)되면 조건 없이 UPDATE가 나가므로, 분리한 뒤 규칙 검사 · 변경만 한다
		entityManager.detach(experience);
		change.accept(experience);
		ExperienceStatus targetStatus = experience.getStatus();
		int targetStep = experience.getLastReviewedStep();
		if (targetStatus == beforeStatus && targetStep == beforeStep) {
			return new TransitionResult(false, targetStatus, targetStep);	// 바뀐 것이 없음 (이미 공개됨, 이미 확인한 섹션 등)
		}

		int updated = experienceRepository.updateStateIfUnchanged(
			experience.getId(), beforeStatus, beforeStep,
			targetStatus, targetStep,
			experience.getPreJudgedAt(), experience.getReviewedAt(), experience.getVerdictConfirmedAt(),
			experience.getAiRevealedAt(), experience.getCompletedAt(), clock.instant());
		if (updated == 1) {
			return new TransitionResult(true, targetStatus, targetStep);
		}

		// 다른 요청이 먼저 처리함
		ExperienceState current = currentState(experience.getId());
		boolean alreadyThere = current.status().ordinal() > targetStatus.ordinal()
			|| (current.status() == targetStatus && current.lastReviewedStep() >= targetStep);
		if (idempotent && alreadyThere) {
			return new TransitionResult(false, current.status(), current.lastReviewedStep());
		}
		throw new InvalidExperienceStateException(current.status(), "다른 요청이 먼저 처리되었습니다.");
	}

	private ExperienceState currentState(Long experienceId) {
		return experienceRepository.findStateById(experienceId)
			.orElseThrow(() -> new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND));
	}

	/** 유니크 제약 위반이면 현재 상태로 깨끗한 INVALID_STATE를, 그 밖의 제약 위반이면 원래 예외를 그대로 돌려준다 */
	private RuntimeException asCleanConflict(Long experienceId, DataIntegrityViolationException e) {
		if (!UniqueViolations.isUniqueViolation(e)) {
			return e;
		}
		// 지금 트랜잭션은 DB가 이미 실패 상태로 만들어서 더 조회할 수 없다. 새 트랜잭션(별도 커넥션)에서 읽는다.
		ExperienceState current = experienceRepository.findStateInNewTransaction(experienceId)
			.orElseThrow(() -> new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND));
		return new InvalidExperienceStateException(current.status(), "다른 요청이 먼저 처리되었습니다.");
	}
}
