package com.team2.project.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 관리자 로그인 요청 */
public record AdminLoginRequest(
	@NotBlank @Size(max = 254) String email,
	@NotBlank @Size(max = 200) String password
) {

	/** 로그 · 예외 메시지에 비밀번호가 찍히지 않게 한다 */
	@Override
	public String toString() {
		return "AdminLoginRequest[email=" + email + "]";
	}
}
