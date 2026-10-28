package com.team2.project.member.domain;

/** 로그인 공급자 (확장 단계, BE-62 · BE-113). DB CHECK(ck_member_provider)와 값을 맞춘다 */
public enum AuthProvider {
	KAKAO,
	/** 아이디 · 비밀번호 회원 (BE-113). provider_user_id는 UUID, 로그인 아이디는 member_password에 둔다 */
	LOCAL
}
