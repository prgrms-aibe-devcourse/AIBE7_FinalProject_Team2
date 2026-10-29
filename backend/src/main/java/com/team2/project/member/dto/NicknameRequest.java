package com.team2.project.member.dto;

/** M-7 닉네임 설정 요청. 형식 검사는 서비스가 한다(Nickname) — 문자열이 아닌 값은 본문 해석 단계에서 400 */
public record NicknameRequest(String nickname) {
}
