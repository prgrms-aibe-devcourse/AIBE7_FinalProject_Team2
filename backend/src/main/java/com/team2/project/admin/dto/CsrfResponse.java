package com.team2.project.admin.dto;

/** CSRF 토큰 안내. 토큰 값은 쿠키(XSRF-TOKEN)로 내려가고, 관리자 상태 변경 요청 때 이 헤더에 담아 보낸다 */
public record CsrfResponse(String headerName) {
}
