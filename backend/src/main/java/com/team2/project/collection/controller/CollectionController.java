package com.team2.project.collection.controller;

import com.team2.project.collection.dto.CollectionRecordRequest;
import com.team2.project.collection.dto.CollectionRecordResponse;
import com.team2.project.collection.dto.CollectionSeenRequest;
import com.team2.project.collection.service.CollectionService;
import com.team2.project.common.web.Requester;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 수집 상태 API (BE-83, API 명세 P-9 · P-10). 조회는 마이페이지(P-5) collection에 함께 내려 준다 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CollectionController {

	private final CollectionService collectionService;

	/** P-9 수집 기록 — 200 + 새로 저장된 항목 · 모은 수 · 새로 해금된 도전과제 */
	@PostMapping("/collection")
	public CollectionRecordResponse record(@Valid @RequestBody CollectionRecordRequest body, Requester requester) {
		return collectionService.record(requester.anonymousId(), body);
	}

	/** P-10 컬렉션 "봤음" — 204 (멱등) */
	@PostMapping("/collection/seen")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markSeen(@Valid @RequestBody CollectionSeenRequest body, Requester requester) {
		collectionService.markSeen(requester.anonymousId(), body);
	}
}
