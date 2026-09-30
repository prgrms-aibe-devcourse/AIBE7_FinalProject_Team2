package com.team2.project.experience.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 체험 (익명 사용자 × 사건 × 회차). 상태 전이 컬럼은 해당 API를 구현할 때 매핑을 추가한다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "experience")
public class Experience {

	/** MVP에서는 회차가 항상 1이다. */
	public static final int FIRST_ATTEMPT = 1;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID anonymousUserId;

	private Long caseId;

	private int attemptNo;

	@Enumerated(EnumType.STRING)
	private ExperienceStatus status;

	private int lastReviewedStep;

	private OffsetDateTime startedAt;

	private OffsetDateTime preJudgedAt;

	private OffsetDateTime updatedAt;

	public static Experience start(UUID anonymousUserId, Long caseId, OffsetDateTime now) {
		Experience experience = new Experience();
		experience.anonymousUserId = anonymousUserId;
		experience.caseId = caseId;
		experience.attemptNo = FIRST_ATTEMPT;
		experience.status = ExperienceStatus.STARTED;
		experience.lastReviewedStep = 0;
		experience.startedAt = now;
		experience.updatedAt = now;
		return experience;
	}
}
