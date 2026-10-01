package com.team2.project.experience.domain;

import com.team2.project.legalcase.domain.LegalCase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 체험 (익명 사용자 × 사건 × 회차)
 * 진행 상태는 앞으로만 이동한다. 동시 요청에 대한 조건부 갱신은 상태 전이 공통 로직(BE-4)에서 다룬다.
 */
@Entity
@Table(name = "experience")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Experience {

	/** MVP는 사건당 1회 체험 (ERD 결정 #5) */
	public static final int FIRST_ATTEMPT = 1;

	/** 섹션 ①(개요)은 S-03에서 본 것으로 처리 */
	private static final int OVERVIEW_STEP = 1;

	/** 사용자가 확인하는 첫 섹션 ②(상세 사실관계) */
	private static final int FIRST_CONFIRM_STEP = 2;

	/** 마지막 섹션 ④(법률 · 양형기준) */
	private static final int LAST_STEP = 4;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "anonymous_user_id")
	private AnonymousUser anonymousUser;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id")
	private LegalCase legalCase;

	private int attemptNo;

	private Long memberId;		// (이후) 체험 시작 시 로그인 상태였다면 회원 ID

	@Enumerated(EnumType.STRING)
	private ExperienceStatus status;

	private int lastReviewedStep;	// S-04에서 확인을 마친 마지막 섹션 (0 ~ 4)

	@CreationTimestamp
	private Instant startedAt;

	private Instant preJudgedAt;

	private Instant reviewedAt;

	private Instant verdictConfirmedAt;

	private Instant aiRevealedAt;

	private Instant completedAt;

	@UpdateTimestamp
	private Instant updatedAt;

	private Experience(AnonymousUser anonymousUser, LegalCase legalCase) {
		this.anonymousUser = anonymousUser;
		this.legalCase = legalCase;
		this.attemptNo = FIRST_ATTEMPT;
		this.status = ExperienceStatus.STARTED;
		this.lastReviewedStep = 0;
	}

	/** 체험 시작 (STARTED, 1회차) */
	public static Experience start(AnonymousUser anonymousUser, LegalCase legalCase) {
		return new Experience(anonymousUser, legalCase);
	}

	/** 사전 판단 제출 → PRE_JUDGED, 섹션 ①은 확인한 것으로 처리 */
	public void markPreJudged(Instant now) {
		moveTo(ExperienceStatus.PRE_JUDGED);
		this.lastReviewedStep = OVERVIEW_STEP;
		this.preJudgedAt = now;
	}

	/**
	 * 섹션 확인 (API 7). 섹션 번호는 2 ~ 4만 받고, 다음 섹션만 확인할 수 있다.
	 * 이미 확인한 섹션이면 아무것도 바꾸지 않고 false를 돌려준다 (버튼 두 번 누름 대비).
	 */
	public boolean confirmReviewStep(int step, Instant now) {
		if (step < FIRST_CONFIRM_STEP || step > LAST_STEP) {
			throw new InvalidReviewStepException(step, FIRST_CONFIRM_STEP, LAST_STEP);
		}
		requireIn(ExperienceStatus.PRE_JUDGED, ExperienceStatus.REVIEWED);
		if (step <= lastReviewedStep) {
			return false;
		}
		if (step != lastReviewedStep + 1) {
			throw new ReviewStepOutOfOrderException(lastReviewedStep + 1, step);
		}
		this.lastReviewedStep = step;
		if (step == LAST_STEP) {
			this.status = ExperienceStatus.REVIEWED;
			this.reviewedAt = now;
		} else {
			this.status = ExperienceStatus.REVIEWING;
		}
		return true;
	}

	/** 판결 확정 → VERDICT_CONFIRMED */
	public void markVerdictConfirmed(Instant now) {
		moveTo(ExperienceStatus.VERDICT_CONFIRMED);
		this.verdictConfirmedAt = now;
	}

	/** 실제 판결 공개 → AI_REVEALED. 이미 공개된 상태면 false (멱등) */
	public boolean revealCourt(Instant now) {
		if (status.isAtLeast(ExperienceStatus.AI_REVEALED)) {
			return false;
		}
		moveTo(ExperienceStatus.AI_REVEALED);
		this.aiRevealedAt = now;
		return true;
	}

	/** 비교 공개 → COMPLETED. 이미 완료 상태면 false (멱등) */
	public boolean revealComparison(Instant now) {
		if (status == ExperienceStatus.COMPLETED) {
			return false;
		}
		moveTo(ExperienceStatus.COMPLETED);
		this.completedAt = now;
		return true;
	}

	/** 바로 다음 상태로만 이동 */
	private void moveTo(ExperienceStatus next) {
		if (!status.isNext(next)) {
			throw new InvalidExperienceStateException(status, status + "에서 " + next + "(으)로 이동할 수 없습니다.");
		}
		this.status = next;
	}

	/** 상태가 from ~ to 범위 안인지 (양 끝 포함) */
	private void requireIn(ExperienceStatus from, ExperienceStatus to) {
		if (status.ordinal() < from.ordinal() || status.ordinal() > to.ordinal()) {
			throw new InvalidExperienceStateException(status, "지금 단계에서는 할 수 없는 요청입니다.");
		}
	}
}
