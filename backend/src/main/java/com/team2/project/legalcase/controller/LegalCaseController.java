package com.team2.project.legalcase.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.dto.CaseListResponse;
import com.team2.project.legalcase.service.LegalCaseService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class LegalCaseController {

	private final LegalCaseService legalCaseService;

	/** API 1. 사건 목록 */
	@GetMapping
	public CaseListResponse getCases(@RequestParam(required = false) CrimeType crimeType) {
		return legalCaseService.getCases(crimeType);
	}
}
