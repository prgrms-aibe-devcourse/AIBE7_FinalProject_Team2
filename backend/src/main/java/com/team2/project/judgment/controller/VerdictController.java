package com.team2.project.judgment.controller;

import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.judgment.dto.VerdictFormResponse;
import com.team2.project.judgment.dto.VerdictRequest;
import com.team2.project.judgment.dto.VerdictResponse;
import com.team2.project.judgment.service.VerdictFormService;
import com.team2.project.judgment.service.VerdictService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/experience")
@RequiredArgsConstructor
public class VerdictController {

	private final VerdictFormService verdictFormService;
	private final VerdictService verdictService;
	private final AnonymousIdCookie anonymousIdCookie;

	/** API 8. 판결 입력 정보 (S-06) */
	@GetMapping("/verdict-form")
	public VerdictFormResponse getForm(@PathVariable Long caseId, HttpServletRequest request) {
		return verdictFormService.getForm(caseId, anonymousIdCookie.read(request));
	}

	/** API 9. 판결 제출 */
	@PostMapping("/verdict")
	public VerdictResponse submit(@PathVariable Long caseId, @Valid @RequestBody VerdictRequest body,
		HttpServletRequest request) {
		return verdictService.submit(caseId, anonymousIdCookie.read(request), body);
	}
}
