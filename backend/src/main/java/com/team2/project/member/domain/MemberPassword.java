package com.team2.project.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아이디 회원의 로그인 정보 (확장 단계, BE-113, V33)
 *
 * 회원 1 : 로그인 정보 0..1 (PK = member_id). 카카오 회원은 행이 없다.
 * 행은 MemberPasswordRepository.insertIfLoginIdAbsent(ON CONFLICT DO NOTHING)로만 만든다 —
 * 같은 아이디로 동시에 가입해도 유니크 위반 예외 없이 한쪽만 들어간다. 이 엔티티는 조회용이다
 */
@Entity
@Table(name = "member_password")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberPassword {

	@Id
	private Long memberId;

	/** 소문자로 바꾼 로그인 아이디 */
	@Column(nullable = false, updatable = false)
	private String loginId;

	/** {argon2} 접두어가 붙은 해시. 원문 비밀번호는 저장하지 않는다 */
	@Column(nullable = false)
	private String passwordHash;

	@Column(nullable = false, insertable = false, updatable = false)
	private Instant changedAt;

	/** 로그 · 예외 메시지에 해시가 찍히지 않게 한다 */
	@Override
	public String toString() {
		return "MemberPassword[memberId=" + memberId + "]";
	}
}
