package com.team2.project.admin.review;

import com.team2.project.admin.review.dto.CasePaperResponse;
import com.team2.project.admin.service.AdminPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신문 지면 콘텐츠 생성 · 조회 · 수정 API (BE-92, COMMON-41). 관리자 체인(세션 · ADMIN 역할)이 지킨다.
 */
@RestController
@RequestMapping("/api/v1/admin/cases/{caseId}/paper")
@RequiredArgsConstructor
public class AdminCasePaperController {

	private final AdminCasePaperService paperService;

	/** 사건 내용을 바탕으로 신문 지면을 생성(또는 다시 생성)한다 */
	@PostMapping("/generate")
	public CasePaperResponse generate(@PathVariable Long caseId, @AuthenticationPrincipal AdminPrincipal admin,
			HttpServletRequest request) {
		return paperService.generatePaper(caseId, admin, request);
	}

	/** 신문 지면 조회 */
	@GetMapping
	public CasePaperResponse get(@PathVariable Long caseId) {
		return paperService.getPaper(caseId);
	}

	/** 신문 지면 수정 (보낸 필드만, null = 지움 — headline · article은 필수라 null 불가) */
	@PatchMapping
	public CasePaperResponse update(@PathVariable Long caseId, @RequestBody(required = false) Map<String, Object> body,
			@AuthenticationPrincipal AdminPrincipal admin, HttpServletRequest request) {
		return paperService.updatePaper(caseId, body, admin, request);
	}
}
