package com.team2.project.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 첫 로그인 가져오기 결정 (확장 단계, BE-140, V42). 회원마다 한 줄 — 있으면 다시 묻지 않는다
 */
@Entity
@Table(name = "member_anonymous_import")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAnonymousImport {

	public enum Decision {
		IMPORTED,	// 체험을 회원 것으로 옮겼다 (M-10)
		DISCARDED	// 옮기지 않았다. 익명 기록은 지우지 않고 둔다 (M-11)
	}

	@Id
	private Long memberId;

	@Column(name = "anonymous_user_id", nullable = false, updatable = false)
	private UUID anonymousUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Decision decision;

	@Column(nullable = false, updatable = false)
	private int experienceCount;

	private MemberAnonymousImport(long memberId, UUID anonymousUserId, Decision decision, int experienceCount) {
		this.memberId = memberId;
		this.anonymousUserId = anonymousUserId;
		this.decision = decision;
		this.experienceCount = experienceCount;
	}

	public static MemberAnonymousImport imported(long memberId, UUID anonymousUserId, int experienceCount) {
		return new MemberAnonymousImport(memberId, anonymousUserId, Decision.IMPORTED, experienceCount);
	}

	public static MemberAnonymousImport discarded(long memberId, UUID anonymousUserId) {
		return new MemberAnonymousImport(memberId, anonymousUserId, Decision.DISCARDED, 0);
	}
}
