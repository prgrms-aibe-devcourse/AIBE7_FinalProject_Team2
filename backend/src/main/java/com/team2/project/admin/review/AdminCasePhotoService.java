package com.team2.project.admin.review;

import com.team2.project.admin.audit.AdminAuditAction;
import com.team2.project.admin.audit.AdminAuditLogger;
import com.team2.project.admin.audit.AuditTargetType;
import com.team2.project.admin.review.dto.AdminCaseDetailResponse;
import com.team2.project.admin.review.dto.IncidentPhotoResponse;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.imagegen.service.CasePhotoService;
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
 * 사건 사진 프롬프트 추천 · 업로드 API (BE-91 재설계, COMMON-41). 사진은 사람이 만들어 업로드하고,
 * 여기서는 사건 행 잠금 · 공개 여부 확인 · 감사 로그만 맡는다(A-11 · A-13과 같은 원칙 — 공개한 사건은 고칠 수 없다).
 */
@Service
@RequiredArgsConstructor
public class AdminCasePhotoService {

	private final LegalCaseRepository caseRepository;
	private final CasePhotoService photoService;
	private final AdminReviewQueryService queryService;
	private final AdminAuditLogger auditLogger;

	/** 사건 대표사진용 한글 프롬프트 추천. 읽기만 하므로 잠그지 않고, 사건 내용도 바꾸지 않는다 */
	public String suggestRepresentativePrompt(Long caseId) {
		return photoService.suggestRepresentativePrompt(findCase(caseId));
	}

	/** 사건 발단 사진용 한글 프롬프트 추천 */
	public String suggestIncidentPrompt(Long caseId, String hint) {
		return photoService.suggestIncidentPrompt(findCase(caseId), hint);
	}

	/** 사건 대표사진(신문용) 업로드. legal_case.thumbnail_url을 그대로 바꾼다(새 테이블을 두지 않는다) */
	@Transactional
	public AdminCaseDetailResponse uploadRepresentativePhoto(Long caseId, byte[] image, String contentType,
			AdminPrincipal admin, HttpServletRequest request) {
		LegalCase legalCase = lockCase(caseId);
		guardNotPublished(legalCase);

		String url = photoService.uploadRepresentativePhoto(caseId, image, contentType);
		legalCase.changeThumbnailUrl(url);
		legalCase.startReview();
		caseRepository.saveAndFlush(legalCase);

		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("type", "REPRESENTATIVE");
		auditLogger.record(admin, AdminAuditAction.CASE_PHOTO_GENERATE, AuditTargetType.CASE, caseId, detail, request);
		caseRepository.flush();
		return queryService.getCase(caseId);
	}

	/** 사건 발단 사진 업로드. 사건마다 순서대로 쌓이고, 몇 장이든 추가할 수 있다 */
	@Transactional
	public IncidentPhotoResponse uploadIncidentPhoto(Long caseId, byte[] image, String contentType, String caption,
			String promptUsed, AdminPrincipal admin, HttpServletRequest request) {
		LegalCase legalCase = lockCase(caseId);
		guardNotPublished(legalCase);

		CaseIncidentPhoto photo = photoService.uploadIncidentPhoto(legalCase, image, contentType, caption, promptUsed);
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
		findCase(caseId);
		return photoService.listIncidentPhotos(caseId).stream().map(IncidentPhotoResponse::from).toList();
	}

	private LegalCase findCase(Long caseId) {
		return caseRepository.findById(caseId).orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));
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
