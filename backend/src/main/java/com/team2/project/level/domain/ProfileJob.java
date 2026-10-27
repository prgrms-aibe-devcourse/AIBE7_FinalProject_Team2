package com.team2.project.level.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자가 카드에 보여 주려고 고른 직업 (BE-106, V30). 익명 사용자당 한 줄 */
@Entity
@Table(name = "profile_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileJob {

	@Id
	private UUID anonymousUserId;

	@Enumerated(EnumType.STRING)
	private Job job;

	private Instant updatedAt;

	public static ProfileJob of(UUID anonymousUserId, Job job, Instant now) {
		ProfileJob profileJob = new ProfileJob();
		profileJob.anonymousUserId = anonymousUserId;
		profileJob.job = job;
		profileJob.updatedAt = now;
		return profileJob;
	}

	public void change(Job job, Instant now) {
		this.job = job;
		this.updatedAt = now;
	}
}
