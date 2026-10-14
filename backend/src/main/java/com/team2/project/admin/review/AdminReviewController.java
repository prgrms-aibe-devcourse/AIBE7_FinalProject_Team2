package com.team2.project.admin.review;

import com.team2.project.admin.review.dto.AdminCaseDetailResponse;
import com.team2.project.admin.review.dto.AdminCaseListResponse;
import com.team2.project.admin.review.dto.AdminJudgmentResponse;
import com.team2.project.admin.review.dto.AdminRejectRequest;
import com.team2.project.admin.review.dto.AdminSourceTextResponse;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.legalcase.domain.CaseStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 검수 조회 API (API 명세 8-2 A-5 ~ A-8, BE-41). 관리자 체인(세션 · ADMIN 역할)이 지킨다(8-1).
 * 쿼리 값은 문자열로 받아 명세대로 엄격히 검사한다(예: pending은 true · false만)
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminReviewController {

	private static final int MAX_SIZE = 100;

	private final AdminReviewQueryService queryService;
	private final AdminReviewDecisionService decisionService;

	/** A-5 검수 사건 목록 */
	@GetMapping("/cases")
	public AdminCaseListResponse listCases(
		@RequestParam(required = false) String status,
		@RequestParam(required = false) String pending,
		@RequestParam(defaultValue = "0") String page,
		@RequestParam(defaultValue = "20") String size) {
		int pageNumber = integer(page, "page");
		int pageSize = integer(size, "size");
		if (pageNumber < 0) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "page");
		}
		if (pageSize < 1 || pageSize > MAX_SIZE) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "size");
		}
		return queryService.listCases(statuses(status), pendingFlag(pending), pageNumber, pageSize);
	}

	/** A-6 사건 검수 상세 */
	@GetMapping("/cases/{caseId}")
	public AdminCaseDetailResponse getCase(@PathVariable Long caseId) {
		return queryService.getCase(caseId);
	}

	/** A-7 원본 판결문 원문 (열람 감사 로그) */
	@GetMapping("/cases/{caseId}/sources/{sourceId}")
	public AdminSourceTextResponse getSourceText(@PathVariable Long caseId, @PathVariable Long sourceId,
		@AuthenticationPrincipal AdminPrincipal admin, HttpServletRequest request) {
		return queryService.getSourceText(caseId, sourceId, admin, request);
	}

	/** A-8 판결 후보 상세 */
	@GetMapping("/judgments/{judgmentId}")
	public AdminJudgmentResponse getJudgment(@PathVariable Long judgmentId) {
		return queryService.getJudgment(judgmentId);
	}

	/** A-9 판결 후보 승인 (= 공개 판결로 정함) */
	@PostMapping("/judgments/{judgmentId}/approve")
	public AdminJudgmentResponse approve(@PathVariable Long judgmentId, @AuthenticationPrincipal AdminPrincipal admin,
		HttpServletRequest request) {
		return decisionService.approve(judgmentId, admin, request);
	}

	/** A-10 판결 후보 반려 */
	@PostMapping("/judgments/{judgmentId}/reject")
	public AdminJudgmentResponse reject(@PathVariable Long judgmentId, @Valid @RequestBody AdminRejectRequest body,
		@AuthenticationPrincipal AdminPrincipal admin, HttpServletRequest request) {
		return decisionService.reject(judgmentId, body.reason(), admin, request);
	}

	private static Set<CaseStatus> statuses(String value) {
		if (value == null) {
			return AdminReviewQueryService.DEFAULT_STATUSES;
		}
		Set<CaseStatus> statuses = EnumSet.noneOf(CaseStatus.class);
		// limit -1: 빈 토큰("DRAFT," · ",")도 남겨 거절한다. 그냥 split이면 빈 배열이 되어 SQL IN ()으로 500이 난다 (#131 리뷰)
		for (String token : value.split(",", -1)) {
			try {
				statuses.add(CaseStatus.valueOf(token.trim()));
			} catch (IllegalArgumentException e) {
				throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "status");
			}
		}
		if (statuses.isEmpty()) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "status");
		}
		return statuses;
	}

	private static Boolean pendingFlag(String value) {
		if (value == null) {
			return null;
		}
		return switch (value) {
			case "true" -> Boolean.TRUE;
			case "false" -> Boolean.FALSE;
			default -> throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "pending");
		};
	}

	private static int integer(String value, String field) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, field);
		}
	}
}
