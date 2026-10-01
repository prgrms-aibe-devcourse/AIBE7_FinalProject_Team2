package com.team2.project.experience.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

/**
 * 익명 사용자 (쿠키 NLNB_AID의 값)
 * id는 쿠키 발급 시 서버 코드에서 만든다 (DB 기본값 없음, ERD 7장)
 * id를 직접 넣으면 Spring Data가 기존 행으로 보고 merge(SELECT 후 INSERT)를 쓰므로,
 * Persistable로 새 객체임을 알려 save()가 바로 INSERT하게 한다.
 */
@Entity
@Table(name = "anonymous_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnonymousUser implements Persistable<UUID> {

	@Id
	private UUID id;

	private Long memberId;		// (이후) 로그인 시 연결할 회원

	@CreationTimestamp
	private Instant createdAt;

	private Instant lastSeenAt;

	@Transient
	private boolean isNew = true;	// 저장 · 조회 후에는 false (Persistable.isNew()로만 노출)

	private AnonymousUser(UUID id, Instant now) {
		this.id = id;
		this.lastSeenAt = now;
	}

	/** 새 익명 사용자 발급 (무작위 UUID) */
	public static AnonymousUser issue(Instant now) {
		return new AnonymousUser(UUID.randomUUID(), now);
	}

	/** 최근 접속 시각 갱신. 갱신 주기 제한은 서비스에서 판단한다 */
	public void touch(Instant now) {
		this.lastSeenAt = now;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
	}
}
