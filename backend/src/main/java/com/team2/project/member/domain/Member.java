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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 (확장 단계, BE-62 · WBS 7.4.2, V25 · BE-113, V33)
 *
 * 카카오 회원: 개인정보(이름 · 이메일 · 프로필)는 받지 않고 공급자 회원번호만 저장한다. nickname은 NULL.
 * 행은 MemberRepository.insertIfAbsent(ON CONFLICT DO NOTHING)로 만든다 — 같은 카카오 계정의 첫 로그인이
 * 동시에 두 번 와도 유니크 위반 없이 한 행만 생긴다.
 *
 * 아이디 회원(LOCAL, BE-113): {@link #local(String)}으로 만든다. provider_user_id는 UUID이고,
 * 로그인 아이디 · 비밀번호 해시는 member_password({@link MemberPassword})에 둔다.
 * 가입할 때 약관 동의 시각 · 버전을 남긴다(BE-133, V40). 카카오 회원 · BE-133 전 가입자는 둘 다 NULL
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

	/** 표시 이름. 아이디 회원만 있고 카카오 회원은 NULL (BE-113) */
	@Column(length = 12)
	private String nickname;

	/** 이용약관 · 개인정보처리방침에 동의한 시각 (BE-133). 아이디 가입 때만 채운다 */
	private Instant termsAgreedAt;

	/** 동의한 약관 버전({@link TermsVersion}). terms_agreed_at과 함께 있거나 함께 없다(CHECK ck_member_terms_agreement) */
	@Column(length = 20)
	private String termsVersion;

	private Member(AuthProvider provider, String providerUserId, String nickname) {
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.nickname = nickname;
	}

	/**
	 * 아이디 회원을 새로 만든다. 공급자 회원번호 자리에는 바뀌지 않는 UUID를 넣는다 (BE-113 결정 1).
	 * 약관 동의는 호출한 쪽이 확인한 뒤 부른다 — 동의 시각과 지금 약관 버전을 함께 남긴다 (BE-133)
	 */
	public static Member local(String nickname, Instant termsAgreedAt) {
		Member member = new Member(AuthProvider.LOCAL, UUID.randomUUID().toString(), nickname);
		member.termsAgreedAt = termsAgreedAt;
		member.termsVersion = TermsVersion.CURRENT;
		return member;
	}

	/** 닉네임을 바꾼다. 형식 · 중복 검사는 서비스가 먼저 한다 */
	public void changeNickname(String nickname) {
		this.nickname = nickname;
	}

	/** 로그인 성공 시각 기록 */
	public void recordLogin(Instant now) {
		this.lastLoginAt = now;
	}
}
