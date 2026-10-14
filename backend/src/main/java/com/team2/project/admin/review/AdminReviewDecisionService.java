package com.team2.project.admin.review;

import com.team2.project.admin.audit.AdminAuditAction;
import com.team2.project.admin.audit.AdminAuditLogger;
import com.team2.project.admin.audit.AuditTargetType;
import com.team2.project.admin.review.dto.AdminJudgmentResponse;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.exception.FieldErrorDetail;
import com.team2.project.common.exception.UniqueViolations;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.ReviewStatus;
import com.team2.project.judgment.repository.AiGenerationRepository;
import com.team2.project.judgment.repository.JudgmentRepository;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판결 후보 승인 · 반려 (API 명세 8-2 A-9 · A-10, BE-42 · WBS 7.4.7).
 * 한 트랜잭션에서 사건 행 잠금 → 상태 확인 → 변경 → 감사 로그(record, 같은 트랜잭션이라 로그가 실패하면 함께 되돌린다).
 * 성공하면 A-8과 같은 형식으로 바뀐 후보를 돌려준다
 */
@Service
@RequiredArgsConstructor
public class AdminReviewDecisionService {

	static final String CASE_PUBLISHED = "CASE_PUBLISHED";
	static final String CANDIDATE_REJECTED = "CANDIDATE_REJECTED";
	static final String CANDIDATE_PUBLISHED = "CANDIDATE_PUBLISHED";

	private final JudgmentRepository judgmentRepository;
	private final LegalCaseRepository caseRepository;
	private final AiGenerationRepository generationRepository;
	private final AdminReviewQueryService queryService;
	private final AdminAuditLogger auditLogger;
	private final Clock clock;

	/**
	 * A-9 승인 = 이 사건의 공개 판결로 정한다. 같은 사건 · 같은 주체의 기존 공개 판결은 비공개로 내린다(행은 지우지 않는다).
	 * 이미 승인돼 공개 중이면 아무것도 바꾸지 않고 그대로 돌려준다(중복 클릭)
	 */
	@Transactional
	public AdminJudgmentResponse approve(Long judgmentId, AdminPrincipal admin, HttpServletRequest request) {
		LegalCase legalCase = lockCaseOf(judgmentId);
		Judgment judgment = judgmentRepository.findById(judgmentId).orElseThrow();
		if (judgment.isPublished() && judgment.reviewState() == ReviewStatus.APPROVED) {
			return queryService.getJudgment(judgmentId);
		}
		if (legalCase.isPublished()) {
			throw invalidState(legalCase, CASE_PUBLISHED);	// 공개한 사건의 판결 교체 금지 (8-3 #1)
		}
		if (judgment.reviewState() == ReviewStatus.REJECTED) {
			throw invalidState(legalCase, CANDIDATE_REJECTED);
		}

		Instant now = clock.instant();
		int replaced = judgmentRepository.unpublishOthers(legalCase.getId(), judgment.getSubjectType(), judgmentId);
		judgment.approve(admin.adminId(), now);
		try {
			judgmentRepository.saveAndFlush(judgment);
		} catch (DataIntegrityViolationException e) {
			// 잠금 밖 경로(SQL 직접 실행 등)로 공개 판결이 생겼을 때. 500이 아니라 409로 돌려준다
			if (UniqueViolations.isUniqueViolation(e)) {
				throw new BusinessException(ErrorCode.INVALID_STATE, ErrorCode.INVALID_STATE.getMessage(),
					legalCase.getStatus().name(), null);
			}
			throw e;
		}
		generationRepository.findByJudgment_Id(judgmentId).ifPresent(generation -> generation.syncReview(ReviewStatus.APPROVED, now));
		legalCase.startReview();

		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("caseId", legalCase.getId());
		detail.put("subjectType", judgment.getSubjectType().name());
		detail.put("unpublishedCount", replaced);
		auditLogger.record(admin, AdminAuditAction.JUDGMENT_APPROVE, AuditTargetType.JUDGMENT, judgmentId, detail, request);
		// 응답 조회(읽기 전용 서비스)에 들어가기 전에 바뀐 내용을 모두 DB에 반영해 둔다
		judgmentRepository.flush();
		return queryService.getJudgment(judgmentId);
	}

	/**
	 * A-10 반려. 공개한 사건에서도 반려할 수 있다(파이프라인이 공개 사건에 새 후보를 쌓으므로). 공개 중인 후보만 막는다.
	 * 반려는 되돌리지 않는다
	 */
	@Transactional
	public AdminJudgmentResponse reject(Long judgmentId, String reason, AdminPrincipal admin, HttpServletRequest request) {
		LegalCase legalCase = lockCaseOf(judgmentId);
		Judgment judgment = judgmentRepository.findById(judgmentId).orElseThrow();
		if (judgment.isPublished()) {
			throw invalidState(legalCase, CANDIDATE_PUBLISHED);
		}
		if (judgment.reviewState() == ReviewStatus.REJECTED) {
			throw invalidState(legalCase, CANDIDATE_REJECTED);
		}

		Instant now = clock.instant();
		judgment.reject(admin.adminId(), now, reason);
		judgmentRepository.saveAndFlush(judgment);
		generationRepository.findByJudgment_Id(judgmentId).ifPresent(generation -> generation.syncReview(ReviewStatus.REJECTED, now));
		legalCase.startReview();

		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("caseId", legalCase.getId());
		detail.put("subjectType", judgment.getSubjectType().name());
		detail.put("reason", reason);
		auditLogger.record(admin, AdminAuditAction.JUDGMENT_REJECT, AuditTargetType.JUDGMENT, judgmentId, detail, request);
		// 응답 조회(읽기 전용 서비스)에 들어가기 전에 바뀐 내용을 모두 DB에 반영해 둔다
		judgmentRepository.flush();
		return queryService.getJudgment(judgmentId);
	}

	/** 후보의 사건 행을 잠근다. 후보가 없거나 사용자 판결이면 404 */
	private LegalCase lockCaseOf(Long judgmentId) {
		Long caseId = judgmentRepository.findCandidateCaseId(judgmentId)
			.orElseThrow(() -> new BusinessException(ErrorCode.JUDGMENT_NOT_FOUND));
		return caseRepository.findByIdForUpdate(caseId).orElseThrow();
	}

	/** 관리자 API의 INVALID_STATE: currentStatus는 사건 상태, 이유는 details 사유 코드 (API 명세 8-2) */
	private static BusinessException invalidState(LegalCase legalCase, String reason) {
		return new BusinessException(ErrorCode.INVALID_STATE, ErrorCode.INVALID_STATE.getMessage(),
			legalCase.getStatus().name(), List.of(new FieldErrorDetail("case", reason)));
	}
}
