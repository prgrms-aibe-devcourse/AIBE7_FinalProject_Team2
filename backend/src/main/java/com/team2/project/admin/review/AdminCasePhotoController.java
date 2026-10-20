package com.team2.project.admin.review;

import com.team2.project.admin.review.dto.AdminCaseDetailResponse;
import com.team2.project.admin.review.dto.CasePhotoPromptResponse;
import com.team2.project.admin.review.dto.IncidentPhotoPromptRequest;
import com.team2.project.admin.review.dto.IncidentPhotoResponse;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사건 사진 프롬프트 추천 · 업로드 API (BE-91 재설계, COMMON-41). 관리자 체인(세션 · ADMIN 역할)이
 * 지킨다(A-5 ~ A-13과 같은 경로 아래). 사진은 Cloudflare 등 AI가 직접 만들지 않고, 추천 프롬프트를
 * 참고해 사람이 만든 사진을 올린다(결정: docs/decisions/BE-91.md).
 */
@RestController
@RequestMapping("/api/v1/admin/cases/{caseId}/photos")
@RequiredArgsConstructor
public class AdminCasePhotoController {

	private final AdminCasePhotoService photoService;

	/** 사건 대표사진(신문용) 한글 프롬프트 추천 */
	@PostMapping("/representative/prompt")
	public CasePhotoPromptResponse suggestRepresentativePrompt(@PathVariable Long caseId) {
		return new CasePhotoPromptResponse(photoService.suggestRepresentativePrompt(caseId));
	}

	/** 사건 발단 사진 한글 프롬프트 추천 */
	@PostMapping("/incident/prompt")
	public CasePhotoPromptResponse suggestIncidentPrompt(@PathVariable Long caseId,
			@Valid @RequestBody(required = false) IncidentPhotoPromptRequest body) {
		String hint = body == null ? null : body.hint();
		return new CasePhotoPromptResponse(photoService.suggestIncidentPrompt(caseId, hint));
	}

	/** 사건 대표사진 업로드. legal_case.thumbnail_url을 바꾼 사건 상세를 그대로 돌려준다 */
	@PostMapping("/representative")
	public AdminCaseDetailResponse uploadRepresentativePhoto(@PathVariable Long caseId,
			@RequestPart("file") MultipartFile file, @AuthenticationPrincipal AdminPrincipal admin,
			HttpServletRequest request) {
		return photoService.uploadRepresentativePhoto(caseId, bytesOf(file), file.getContentType(), admin, request);
	}

	/** 사건 발단 사진 업로드 (여러 번 호출해 순서대로 쌓는다) */
	@PostMapping("/incident")
	@ResponseStatus(HttpStatus.CREATED)
	public IncidentPhotoResponse uploadIncidentPhoto(@PathVariable Long caseId, @RequestPart("file") MultipartFile file,
			@RequestParam(required = false) String caption, @RequestParam(required = false) String prompt,
			@AuthenticationPrincipal AdminPrincipal admin, HttpServletRequest request) {
		return photoService.uploadIncidentPhoto(caseId, bytesOf(file), file.getContentType(), caption, prompt, admin,
			request);
	}

	/** 사건 발단 사진 목록 (순서대로) */
	@GetMapping("/incident")
	public List<IncidentPhotoResponse> listIncidentPhotos(@PathVariable Long caseId) {
		return photoService.listIncidentPhotos(caseId);
	}

	private static byte[] bytesOf(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw BusinessException.ofField(ErrorCode.INVALID_IMAGE_FILE, "file");
		}
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw BusinessException.ofField(ErrorCode.INVALID_IMAGE_FILE, "file");
		}
	}
}
