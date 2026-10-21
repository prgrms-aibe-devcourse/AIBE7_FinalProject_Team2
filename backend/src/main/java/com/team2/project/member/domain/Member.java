package com.team2.project.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 (확장 단계, BE-62 · WBS 7.4.2, V24)
 *
 * 카카오 로그인으로만 만든다. 개인정보(이름 · 이메일 · 프로필)는 받지 않고 공급자 회원번호만 저장한다.
 * 행은 MemberRepository.insertIfAbsent(ON CONFLICT DO NOTHING)로 만든다 — 같은 카카오 계정의 첫 로그인이
 * 동시에 두 번 와도 유니크 위반 없이 한 행만 생긴다
 */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private AuthProvider provider;

	@Column(nullable = false, updatable = false)
	private String providerUserId;

	@Column(nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	private Instant lastLoginAt;

	/** 로그인 성공 시각 기록 */
	public void recordLogin(Instant now) {
		this.lastLoginAt = now;
	}
}
