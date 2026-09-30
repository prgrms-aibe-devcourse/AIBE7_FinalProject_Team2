package com.team2.project.experience.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 익명 사용자. id는 브라우저 쿠키 NLNB_AID의 값이며 서버가 직접 생성한다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "anonymous_user")
public class AnonymousUser {

	@Id
	private UUID id;

	private OffsetDateTime createdAt;

	private OffsetDateTime lastSeenAt;

	public AnonymousUser(UUID id, OffsetDateTime now) {
		this.id = id;
		this.createdAt = now;
		this.lastSeenAt = now;
	}
}
