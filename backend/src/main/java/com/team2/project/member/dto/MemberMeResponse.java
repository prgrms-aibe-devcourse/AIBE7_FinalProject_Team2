package com.team2.project.member.dto;

import java.time.OffsetDateTime;

/**
 * 내 회원 정보 (확장 단계, BE-139, API 명세 M-8)
 *
 * @param nickname    닉네임. 아직 정하지 않았으면(카카오 회원) null
 * @param displayName 화면 · 커뮤니티에 보이는 이름. 닉네임이 없으면 `방청객 {회원 ID}`
 * @param provider    로그인 방식 KAKAO · LOCAL
 * @param loginId     아이디 회원의 로그인 아이디(소문자). 카카오 회원은 null
 * @param createdAt   가입 시각
 * @param lastLoginAt 최근 로그인 시각. 기록이 없으면 null
 */
public record MemberMeResponse(String nickname, String displayName, String provider, String loginId,
	OffsetDateTime createdAt, OffsetDateTime lastLoginAt) {
}
