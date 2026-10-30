package com.team2.project.illustration.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team2.project.illustration.dto.IllustrationSummary;
import com.team2.project.illustration.service.IllustrationCatalog;

import lombok.RequiredArgsConstructor;

/** 일러스트 API (BE-142, API 명세 I-1). 누구나 부를 수 있고 쿠키는 읽지도 발급하지도 않는다 */
@RestController
@RequestMapping("/api/v1/illustrations")
@RequiredArgsConstructor
public class IllustrationController {

	private final IllustrationCatalog catalog;

	/** I-1 일러스트 총 개수 — 사건 컷 · 캐릭터(아이콘 포함) 전부 */
	@GetMapping("/summary")
	public IllustrationSummary summary() {
		return catalog.summary();
	}
}
