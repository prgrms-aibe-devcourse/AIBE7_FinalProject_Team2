package com.team2.project.admin.controller;

import com.team2.project.admin.dto.AdminLoginRequest;
import com.team2.project.admin.dto.AdminMeResponse;
import com.team2.project.admin.dto.CsrfResponse;
import com.team2.project.admin.service.AdminAuthService;
import com.team2.project.admin.service.AdminPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 인증 API (확장 단계, BE-39). 경로 권한은 SecurityConfig에서 정한다 */
@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

	private final AdminAuthService authService;

	/** CSRF 토큰 발급. 토큰은 쿠키(XSRF-TOKEN)로 내려간다. 로그인 전에 먼저 부른다 */
	@GetMapping("/csrf")
	public CsrfResponse csrf(CsrfToken token) {
		token.getToken();	// 지연 생성된 토큰을 여기서 만들어 쿠키로 내려보낸다
		return new CsrfResponse(token.getHeaderName());
	}

	/** 로그인. 성공하면 세션 쿠키(NLNB_ADMIN_SESSION)를 내려준다 */
	@PostMapping("/login")
	public AdminMeResponse login(@Valid @RequestBody AdminLoginRequest login, HttpServletRequest request,
		HttpServletResponse response) {
		return authService.login(login, request, response);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(HttpServletRequest request) {
		authService.logout(request);
	}

	/** 로그인한 관리자 정보. 로그인하지 않았으면 401 UNAUTHORIZED */
	@GetMapping("/me")
	public AdminMeResponse me(@AuthenticationPrincipal AdminPrincipal principal) {
		return AdminMeResponse.from(principal);
	}
}
