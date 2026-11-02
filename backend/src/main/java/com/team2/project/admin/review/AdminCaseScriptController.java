package com.team2.project.admin.review;

import com.team2.project.admin.review.dto.AdminRejectRequest;
import com.team2.project.admin.review.dto.CaseScriptLineRequest;
import com.team2.project.admin.review.dto.CaseScriptResponse;
import com.team2.project.admin.review.dto.ScriptRegenerationRequest;
import com.team2.project.admin.review.dto.ScriptRegenerationResponse;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사건 대본 검수 API (BE-108, A-13-10 · A-13-11 · A-13-12). 관리자 체인(세션 · ADMIN 역할)이 지킨다.
 * 대본은 사건 등록 적재(A-14-1)에서 비공개로 들어오고, 여기서 승인해야 공개된다(BE-109가 서빙).
 * 블록 재생성(A-13-15 ~ 17, BE-129 2차)도 여기 있다.
 */
@RestController
@RequestMapping("/api/v1/admin/cases/{caseId}/script")
@RequiredArgsConstructor
public class AdminCaseScriptController {

	private final AdminCaseScriptService scriptService;
	private final AdminScriptRegenerationService regenerationService;

	/** A-13-10 대본 조회 (검수 화면) */
	@GetMapping
	public CaseScriptResponse get(@PathVariable Long caseId) {
		return scriptService.getScript(caseId);
	}

	/** A-13-11 대본 승인 (= 공개) */
	@PostMapping("/approve")
	public CaseScriptResponse approve(@PathVariable Long caseId, @AuthenticationPrincipal AdminPrincipal admin,
			HttpServletRequest request) {
		return scriptService.approve(caseId, admin, request);
	}

	/** A-13-12 대본 반려 */
	@PostMapping("/reject")
	public CaseScriptResponse reject(@PathVariable Long caseId, @Valid @RequestBody AdminRejectRequest body,
			@AuthenticationPrincipal AdminPrincipal admin, HttpServletRequest request) {
		return scriptService.reject(caseId, body.reason(), admin, request);
	}

	/** A-13-13 대사 한 줄 직접 수정. factorId는 파이프라인 번호(= factor.display_order)다 */
	@PatchMapping("/evidence/{factorId}")
	public CaseScriptResponse editLine(@PathVariable Long caseId, @PathVariable int factorId,
			@Valid @RequestBody CaseScriptLineRequest body, @AuthenticationPrincipal AdminPrincipal admin,
			HttpServletRequest request) {
		String violation = body.lengthViolation();
		if (violation != null) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, violation);
		}
		return scriptService.editLine(caseId, factorId, body.text(), admin, request);
	}

	/** A-13-15 블록 재생성 요청. 파이프라인이 다시 만드는 동안 기다리지 않는다(202) */
	@PostMapping("/regenerations")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ScriptRegenerationResponse regenerate(@PathVariable Long caseId,
			@Valid @RequestBody ScriptRegenerationRequest body, @AuthenticationPrincipal AdminPrincipal admin,
			HttpServletRequest request) {
		return regenerationService.start(caseId, body, admin, request);
	}

	/** A-13-16 재생성 상태 · 미리보기 (화면이 끝날 때까지 묻는다) */
	@GetMapping("/regenerations/{regenerationId}")
	public ScriptRegenerationResponse getRegeneration(@PathVariable Long caseId, @PathVariable Long regenerationId) {
		return regenerationService.get(caseId, regenerationId);
	}

	/** A-13-17 재생성 결과 반영. 바꾼 블록은 다시 검수 대기다 */
	@PostMapping("/regenerations/{regenerationId}/apply")
	public CaseScriptResponse applyRegeneration(@PathVariable Long caseId, @PathVariable Long regenerationId,
			@AuthenticationPrincipal AdminPrincipal admin, HttpServletRequest request) {
		return regenerationService.apply(caseId, regenerationId, admin, request);
	}
}
