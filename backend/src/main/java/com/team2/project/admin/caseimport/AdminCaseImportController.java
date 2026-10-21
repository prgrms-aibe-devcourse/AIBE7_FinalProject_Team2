package com.team2.project.admin.caseimport;

import com.team2.project.admin.caseimport.dto.CaseImportCreateRequest;
import com.team2.project.admin.caseimport.dto.CaseImportCreatedResponse;
import com.team2.project.admin.caseimport.dto.CaseImportResponse;
import com.team2.project.admin.service.AdminPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사건 등록 (BE-93, API 명세 A-14 · A-15).
 *
 * <p>관리자 인증 · CSRF는 SecurityConfig가 /api/v1/admin/** 전체에 걸어 둔다 — 여기에 따로 적지 않는다.
 */
@RestController
@RequestMapping("/api/v1/admin/case-imports")
@RequiredArgsConstructor
public class AdminCaseImportController {

	private final AdminCaseImportService importService;

	/** A-14. 파이프라인은 수십 분 걸려서 기다리지 않고 202로 답한다 */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public CaseImportCreatedResponse startImport(@Valid @RequestBody CaseImportCreateRequest request,
			@AuthenticationPrincipal AdminPrincipal admin, HttpServletRequest httpRequest) {
		return importService.startImport(request, admin, httpRequest);
	}

	/** A-15. 화면이 끝날 때까지 2초마다 묻는다 */
	@GetMapping("/{importId}")
	public CaseImportResponse getImport(@PathVariable Long importId) {
		return importService.getImport(importId);
	}
}
