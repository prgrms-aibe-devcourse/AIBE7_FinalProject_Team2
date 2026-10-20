package com.team2.project.admin.review;

import com.team2.project.admin.review.dto.AdminCaseDetailResponse;
import com.team2.project.admin.review.dto.GenerateIncidentPhotoRequest;
import com.team2.project.admin.review.dto.GenerateRepresentativePhotoRequest;
import com.team2.project.admin.review.dto.IncidentPhotoResponse;
import com.team2.project.admin.service.AdminPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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
 * 사건 사진 생성 API (BE-91, COMMON-41). 관리자 체인(세션 · ADMIN 역할)이 지킨다(A-5 ~ A-13과 같은 경로 아래).
 * Cloudflare Workers AI로 생성 → S3 업로드 → DB 기록까지 한 호출로 끝낸다(동기, 수십 초 걸릴 수 있다)
 */
@RestController
@RequestMapping("/api/v1/admin/cases/{caseId}/photos")
@RequiredArgsConstructor
public class AdminCasePhotoController {

	private final AdminCasePhotoService photoService;

	/** 사건 대표사진(신문용) 생성. legal_case.thumbnail_url을 바꾼 사건 상세를 그대로 돌려준다 */
	@PostMapping("/representative")
	public AdminCaseDetailResponse generateRepresentativePhoto(@PathVariable Long caseId,
			@Valid @RequestBody GenerateRepresentativePhotoRequest body, @AuthenticationPrincipal AdminPrincipal admin,
			HttpServletRequest request) {
		return photoService.generateRepresentativePhoto(caseId, body.caseSummary(), admin, request);
	}

	/** 사건 발단 사진 생성 (여러 번 호출해 순서대로 쌓는다) */
	@PostMapping("/incident")
	@ResponseStatus(HttpStatus.CREATED)
	public IncidentPhotoResponse generateIncidentPhoto(@PathVariable Long caseId,
			@Valid @RequestBody GenerateIncidentPhotoRequest body, @AuthenticationPrincipal AdminPrincipal admin,
			HttpServletRequest request) {
		return photoService.generateIncidentPhoto(caseId, body.incidentDescription(), body.caption(), admin, request);
	}

	/** 사건 발단 사진 목록 (순서대로) */
	@GetMapping("/incident")
	public List<IncidentPhotoResponse> listIncidentPhotos(@PathVariable Long caseId) {
		return photoService.listIncidentPhotos(caseId);
	}
}
