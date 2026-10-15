package com.team2.project.legalcase.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.dto.CaseListResponse;
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
	private final AnonymousIdCookie anonymousIdCookie;

	/**
	 * API 1. 사건 목록. 익명 ID 쿠키가 있으면 사건마다 내 진행 상태(myStatus)를 붙인다 (FE-19).
	 * 목록은 둘러보기 화면이라 쿠키를 새로 발급하지 않는다 — 발급은 체험 시작(API 2)에서만.
	 */
	@GetMapping
	public CaseListResponse getCases(@RequestParam(required = false) CrimeType crimeType, HttpServletRequest request) {
		return legalCaseService.getCases(crimeType, anonymousIdCookie.read(request));
	}
}
