package com.team2.project.legalcase.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.dto.CaseDetailResponse;
import com.team2.project.legalcase.dto.CaseListResponse;
import com.team2.project.legalcase.dto.PublicCasePaperResponse;
import com.team2.project.legalcase.dto.PublicCaseScriptResponse;
import com.team2.project.legalcase.service.CasePaperQueryService;
import com.team2.project.legalcase.service.CaseScriptQueryService;
import com.team2.project.legalcase.service.LegalCaseService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class LegalCaseController {

	private final LegalCaseService legalCaseService;
	private final CasePaperQueryService casePaperQueryService;
	private final CaseScriptQueryService caseScriptQueryService;
	private final AnonymousIdCookie anonymousIdCookie;

	/**
	 * API 1. 사건 목록. 익명 ID 쿠키가 있으면 사건마다 내 진행 상태(myStatus)를 붙인다 (FE-19).
	 * 목록은 둘러보기 화면이라 쿠키를 새로 발급하지 않는다 — 발급은 체험 시작(API 2)에서만.
	 */
	@GetMapping
	public CaseListResponse getCases(@RequestParam(required = false) CrimeType crimeType, HttpServletRequest request) {
		return legalCaseService.getCases(crimeType, anonymousIdCookie.read(request));
	}

	/**
	 * API 16. 공개 사건 상세 (S-20 사건 디테일, BE-74). 체험 없이 개요 · 죄명 · 카드 값 · 내 진행 상태를 준다.
	 * 법정형 · 권고 범위 · 판결은 넣지 않는다(FR-2-8). 쿠키는 읽기만 하고 발급하지 않는다(API 1과 같음).
	 */
	@GetMapping("/{caseId}")
	public CaseDetailResponse getCase(@PathVariable Long caseId, HttpServletRequest request) {
		return legalCaseService.getCase(caseId, anonymousIdCookie.read(request));
	}

	/**
	 * API 17. 공개 신문 지면 (BE-102). 신문 지면(BE-92) · 발단·결론 사진(BE-91 · BE-101)을 그대로
	 * 가져다 조립한다. 발단 · 결론 사진은 고정 자리({@code extraPhotos.origin} · {@code .conclusion})로
	 * 준다 — 배열이 아니다(BE-78 2차 검토 참고).
	 */
	@GetMapping("/{caseId}/paper")
	public PublicCasePaperResponse getCasePaper(@PathVariable Long caseId) {
		return casePaperQueryService.getCasePaper(caseId);
	}

	/**
	 * API 18. 공개 사건 대본 (BE-109). BE-107이 만들고 BE-108에서 승인한 2막 증거조사 대사만 준다 —
	 * 나머지 막은 사건에 상관없는 공통 틀로 FE-33이 채운다(해송님 · 승호님 합의, 2026-10-27). 비공개
	 * 사건은 CASE_NOT_FOUND, 대본이 없거나 아직 승인 전이면 SCRIPT_NOT_FOUND(API 17과 같은 모양) —
	 * 프론트는 404를 받으면 대본 없는 사건과 같이 MVP 화면으로 간다(scripts.js 원칙).
	 */
	@GetMapping("/{caseId}/script")
	public PublicCaseScriptResponse getCaseScript(@PathVariable Long caseId) {
		return caseScriptQueryService.getCaseScript(caseId);
	}
}
