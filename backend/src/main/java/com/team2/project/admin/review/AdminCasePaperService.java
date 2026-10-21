package com.team2.project.admin.review;

import com.team2.project.admin.audit.AdminAuditAction;
import com.team2.project.admin.audit.AdminAuditLogger;
import com.team2.project.admin.audit.AuditTargetType;
import com.team2.project.admin.review.dto.CasePaperResponse;
import com.team2.project.admin.review.dto.IncidentPhotoResponse;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.imagegen.service.CasePaperService;
import com.team2.project.legalcase.domain.CasePaper;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.repository.CaseIncidentPhotoRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 신문 지면 생성 · 조회 · 수정 API (BE-92, COMMON-41). 사건 행 잠금 · 공개 여부 확인 · 감사 로그는
 * A-11 · A-13 · BE-91(사진)과 같은 원칙을 따른다 — 공개한 사건은 고칠 수 없다.
 */
@Service
public class AdminCasePaperService {

	private final LegalCaseRepository caseRepository;
	private final CasePaperService paperService;
	private final CaseIncidentPhotoRepository incidentPhotoRepository;
	private final AdminAuditLogger auditLogger;
	private final TransactionTemplate transactionTemplate;

	public AdminCasePaperService(LegalCaseRepository caseRepository, CasePaperService paperService,
			CaseIncidentPhotoRepository incidentPhotoRepository, AdminAuditLogger auditLogger,
			PlatformTransactionManager transactionManager) {
		this.caseRepository = caseRepository;
		this.paperService = paperService;
		this.incidentPhotoRepository = incidentPhotoRepository;
		this.auditLogger = auditLogger;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/**
	 * 신문 지면 생성. Gemini 호출(40 ~ 90초)은 사건 행 잠금 없이 먼저 끝내고, 저장(짧은 트랜잭션)에서만
	 * 잠근다 — 그렇지 않으면 그 시간만큼 다른 쓰기 요청(A-11 수정 · 사진 업로드 · 공개 처리)이 막힌다(PR #212 리뷰).
	 * 생성하는 동안 사건이 공개될 수 있어 저장 직전에 공개 여부를 다시 확인한다
	 */
	public CasePaperResponse generatePaper(Long caseId, AdminPrincipal admin, HttpServletRequest request) {
		LegalCase snapshot = findCase(caseId);
		guardNotPublished(snapshot);

		CasePaperService.GeneratedPaper generated = paperService.generateContent(snapshot);

		return transactionTemplate.execute(status -> {
			LegalCase legalCase = lockCase(caseId);
			guardNotPublished(legalCase);
			CasePaper paper = paperService.savePaper(legalCase, generated);
			legalCase.startReview();
			caseRepository.saveAndFlush(legalCase);

			auditLogger.record(admin, AdminAuditAction.CASE_PAPER_GENERATE, AuditTargetType.CASE, caseId, Map.of(),
				request);
			return toResponse(legalCase, paper);
		});
	}

	public CasePaperResponse getPaper(Long caseId) {
		LegalCase legalCase = findCase(caseId);
		return toResponse(legalCase, paperService.getPaper(caseId));
	}

	@Transactional
	public CasePaperResponse updatePaper(Long caseId, Map<String, Object> body, AdminPrincipal admin,
			HttpServletRequest request) {
		CasePaperEditRequest edit = CasePaperEditRequest.parse(body);
		LegalCase legalCase = lockCase(caseId);
		guardNotPublished(legalCase);
		CasePaper paper = paperService.getPaper(caseId);

		if (edit.has("headline")) {
			paper.changeHeadline(edit.get("headline"));
		}
		if (edit.has("deck")) {
			paper.changeDeck(edit.get("deck"));
		}
		if (edit.has("caption")) {
			paper.changeCaption(edit.get("caption"));
		}
		if (edit.has("article")) {
			paper.changeArticle(edit.get("article"));
		}
		legalCase.startReview();
		caseRepository.saveAndFlush(legalCase);

		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("fields", edit.changes().keySet());
		auditLogger.record(admin, AdminAuditAction.CASE_PAPER_UPDATE, AuditTargetType.CASE, caseId, detail, request);
		return toResponse(legalCase, paper);
	}

	private CasePaperResponse toResponse(LegalCase legalCase, CasePaper paper) {
		var extraPhotos = incidentPhotoRepository.findByLegalCaseIdOrderByDisplayOrder(legalCase.getId()).stream()
			.map(IncidentPhotoResponse::from)
			.toList();
		return CasePaperResponse.of(paper, legalCase.getThumbnailUrl(), extraPhotos);
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
