package com.team2.project.admin.review;

import com.team2.project.admin.audit.AdminAuditAction;
import com.team2.project.admin.audit.AdminAuditLogger;
import com.team2.project.admin.audit.AuditTargetType;
import com.team2.project.admin.review.dto.AdminCaseDetailResponse;
import com.team2.project.admin.review.dto.IncidentPhotoResponse;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.imagegen.service.CaseImageGenerationService;
import com.team2.project.legalcase.domain.CaseIncidentPhoto;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사건 사진 생성 API (BE-91, COMMON-41). Cloudflare 호출 · S3 업로드는 {@link CaseImageGenerationService}가 하고,
 * 여기서는 사건 행 잠금 · 공개 여부 확인 · 감사 로그만 맡는다(A-11 · A-13과 같은 원칙 — 공개한 사건은 고칠 수 없다)
 */
@Service
@RequiredArgsConstructor
public class AdminCasePhotoService {

	private final LegalCaseRepository caseRepository;
	private final CaseImageGenerationService imageGenerationService;
	private final AdminReviewQueryService queryService;
	private final AdminAuditLogger auditLogger;

	/** 사건 대표사진(신문용) 생성. legal_case.thumbnail_url을 그대로 바꾼다(새 테이블을 두지 않는다, docs/decisions/BE-91.md) */
	@Transactional
	public AdminCaseDetailResponse generateRepresentativePhoto(Long caseId, String caseSummary, AdminPrincipal admin,
			HttpServletRequest request) {
		LegalCase legalCase = lockCase(caseId);
		guardNotPublished(legalCase);

		String url = imageGenerationService.generateRepresentativePhoto(caseId, caseSummary);
		legalCase.changeThumbnailUrl(url);
		legalCase.startReview();
		caseRepository.saveAndFlush(legalCase);

		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("type", "REPRESENTATIVE");
		auditLogger.record(admin, AdminAuditAction.CASE_PHOTO_GENERATE, AuditTargetType.CASE, caseId, detail, request);
		caseRepository.flush();
		return queryService.getCase(caseId);
	}

	/** 사건 발단 사진 생성. 사건마다 순서대로 쌓이고, 몇 장이든 추가할 수 있다 */
	@Transactional
	public IncidentPhotoResponse generateIncidentPhoto(Long caseId, String incidentDescription, String caption,
			AdminPrincipal admin, HttpServletRequest request) {
		LegalCase legalCase = lockCase(caseId);
		guardNotPublished(legalCase);

		CaseIncidentPhoto photo = imageGenerationService.generateIncidentPhoto(legalCase, incidentDescription, caption);
		legalCase.startReview();
		caseRepository.saveAndFlush(legalCase);

		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("type", "INCIDENT");
		detail.put("photoId", photo.getId());
		auditLogger.record(admin, AdminAuditAction.CASE_PHOTO_GENERATE, AuditTargetType.CASE, caseId, detail, request);
		return IncidentPhotoResponse.from(photo);
	}

	/** 사건 발단 사진 목록 (순서대로). 읽기만 하므로 잠그지 않는다 */
	public List<IncidentPhotoResponse> listIncidentPhotos(Long caseId) {
		if (!caseRepository.existsById(caseId)) {
			throw new BusinessException(ErrorCode.CASE_NOT_FOUND);
		}
		return imageGenerationService.listIncidentPhotos(caseId).stream().map(IncidentPhotoResponse::from).toList();
	}

	private LegalCase lockCase(Long caseId) {
		return caseRepository.findByIdForUpdate(caseId).orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));
	}

	private static void guardNotPublished(LegalCase legalCase) {
		if (legalCase.isPublished()) {
			throw AdminReviewDecisionService.invalidState(legalCase, AdminReviewDecisionService.CASE_PUBLISHED);
		}
	}
}
