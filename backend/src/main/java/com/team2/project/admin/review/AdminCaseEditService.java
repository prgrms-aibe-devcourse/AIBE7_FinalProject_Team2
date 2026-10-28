package com.team2.project.admin.review;

import com.team2.project.admin.audit.AdminAuditAction;
import com.team2.project.admin.audit.AdminAuditLogger;
import com.team2.project.admin.audit.AuditTargetType;
import com.team2.project.admin.review.dto.AdminCaseDetailResponse;
import com.team2.project.admin.review.dto.AdminValueAxisResponse;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.exception.FieldErrorDetail;
import com.team2.project.common.exception.UniqueViolations;
import com.team2.project.legalcase.domain.Difficulty;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.ValueAxis;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사건 내용 수정 · 가치관 축 확정 · 사건 공개 · 공개 취소 (API 명세 8-2 A-11 ~ A-13 · A-13-14, BE-43 · BE-118 · WBS 7.4.7).
 * 한 트랜잭션에서 사건 행 잠금 → 상태 확인 → 변경 → 감사 로그(record, 같은 트랜잭션). 사건이 DRAFT면 REVIEW로 바꾼다(공개는 제외)
 */
@Service
@RequiredArgsConstructor
public class AdminCaseEditService {

	private final LegalCaseRepository caseRepository;
	private final FactorRepository factorRepository;
	private final AdminReviewQueryService queryService;
	private final AdminAuditLogger auditLogger;
	private final Clock clock;

	/** A-11 사건 내용 수정. 보낸 필드만 바꾼다. 공개한 사건은 고칠 수 없다 */
	@Transactional
	public AdminCaseDetailResponse edit(Long caseId, Map<String, Object> body, AdminPrincipal admin,
		HttpServletRequest request) {
		CaseEditRequest edit = CaseEditRequest.parse(body);	// 잠금 전에 본문부터 검사한다
		LegalCase legalCase = lockCase(caseId);
		if (legalCase.isPublished()) {
			throw AdminReviewDecisionService.invalidState(legalCase, AdminReviewDecisionService.CASE_PUBLISHED);
		}

		// 권고 하한 · 상한은 하나만 보내도 저장된 다른 값과 비교한다
		Integer minMonths = edit.has("recommendedMinMonths") ? edit.get("recommendedMinMonths") : legalCase.getRecommendedMinMonths();
		Integer maxMonths = edit.has("recommendedMaxMonths") ? edit.get("recommendedMaxMonths") : legalCase.getRecommendedMaxMonths();
		if (minMonths != null && maxMonths != null && minMonths > maxMonths) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "recommendedMinMonths");
		}
		if (edit.has("title") && caseRepository.existsByTitleAndIdNot(edit.get("title"), caseId)) {
			throw new BusinessException(ErrorCode.DUPLICATE_TITLE);
		}

		if (edit.has("title")) {
			legalCase.changeTitle(edit.get("title"));
		}
		if (edit.has("shortIntro")) {
			legalCase.changeShortIntro(edit.get("shortIntro"));
		}
		if (edit.has("overview")) {
			legalCase.changeOverview(edit.get("overview"));
		}
		if (edit.has("keywords")) {
			legalCase.changeKeywords(edit.<List<String>>get("keywords"));
		}
		if (edit.has("difficulty")) {
			legalCase.changeDifficulty(edit.<Difficulty>get("difficulty"));
		}
		if (edit.has("estimatedMinutes")) {
			legalCase.changeEstimatedMinutes(edit.get("estimatedMinutes"));
		}
		if (edit.has("thumbnailUrl")) {
			legalCase.changeThumbnailUrl(edit.get("thumbnailUrl"));
		}
		if (edit.has("recommendedMinMonths") || edit.has("recommendedMaxMonths")) {
			legalCase.changeRecommendedRange(minMonths, maxMonths);
		}
		if (edit.has("recommendedBasis")) {
			legalCase.changeRecommendedBasis(edit.get("recommendedBasis"));
		}
		legalCase.startReview();
		try {
			caseRepository.saveAndFlush(legalCase);
		} catch (DataIntegrityViolationException e) {
			// 확인과 저장 사이에 다른 사건이 같은 제목을 쓴 경우
			if (UniqueViolations.isUniqueViolation(e)) {
				throw new BusinessException(ErrorCode.DUPLICATE_TITLE);
			}
			throw e;
		}

		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("fields", List.copyOf(edit.changes().keySet()));	// 값은 남기지 않는다(본문은 사건 내용이라 길다)
		auditLogger.record(admin, AdminAuditAction.CASE_UPDATE, AuditTargetType.CASE, caseId, detail, request);
		caseRepository.flush();
		return queryService.getCase(caseId);
	}

	/** A-12 판단 요소 가치관 축 확정. 공개한 사건에서도 고칠 수 있다(지난 체험의 성향은 저장 시점 값으로 고정, 성향 산식 3-4) */
	@Transactional
	public AdminValueAxisResponse confirmValueAxis(Long factorId, Map<String, Object> body, AdminPrincipal admin,
		HttpServletRequest request) {
		ValueAxis axis = valueAxis(body);
		Factor factor = factorRepository.findById(factorId)
			.orElseThrow(() -> new BusinessException(ErrorCode.FACTOR_NOT_FOUND));
		LegalCase legalCase = lockCase(factor.getLegalCase().getId());
		ValueAxis previous = factor.getValueAxis();
		factor.confirmValueAxis(axis);
		legalCase.startReview();

		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("caseId", legalCase.getId());
		detail.put("from", previous == null ? null : previous.name());
		detail.put("to", axis == null ? null : axis.name());
		auditLogger.record(admin, AdminAuditAction.VALUE_AXIS_CONFIRM, AuditTargetType.FACTOR, factorId, detail, request);
		return new AdminValueAxisResponse(factor.getId(), factor.getValueAxis(), factor.getValueAxisStatus());
	}

	/** A-13 사건 공개. 공개 조건(PublishReadiness)을 모두 채워야 한다. 가치관 축이 AUTO인 요소가 있어도 막지 않는다 */
	@Transactional
	public AdminCaseDetailResponse publish(Long caseId, AdminPrincipal admin, HttpServletRequest request) {
		LegalCase legalCase = lockCase(caseId);
		if (legalCase.isPublished()) {
			throw AdminReviewDecisionService.invalidState(legalCase, AdminReviewDecisionService.CASE_PUBLISHED);
		}
		PublishReadiness readiness = queryService.readinessOf(caseId);
		if (!readiness.ready()) {
			throw new BusinessException(ErrorCode.PUBLISH_NOT_READY, ErrorCode.PUBLISH_NOT_READY.getMessage(),
				legalCase.getStatus().name(),
				readiness.reasons().stream().map(reason -> new FieldErrorDetail("case", reason)).toList());
		}
		legalCase.publish(clock.instant());
		caseRepository.saveAndFlush(legalCase);
		auditLogger.record(admin, AdminAuditAction.CASE_PUBLISH, AuditTargetType.CASE, caseId, null, request);
		caseRepository.flush();
		return queryService.getCase(caseId);
	}

	/**
	 * A-13-14 사건 공개 취소 (BE-118). PUBLISHED → REVIEW, 공개 시각을 지운다. 사용자 API는 PUBLISHED만 읽으므로
	 * 목록 · 상세 · 체험 · 결과에서 바로 사라진다. 행 · 판결 · 사용자 체험 기록은 지우지 않는다(숨김, 1단계).
	 * 다시 공개하려면 A-13을 다시 부른다(공개 조건을 다시 판정)
	 */
	@Transactional
	public AdminCaseDetailResponse unpublish(Long caseId, String reason, AdminPrincipal admin, HttpServletRequest request) {
		LegalCase legalCase = lockCase(caseId);
		if (!legalCase.isPublished()) {
			throw AdminReviewDecisionService.invalidState(legalCase, AdminReviewDecisionService.CASE_NOT_PUBLISHED);
		}
		legalCase.unpublish();
		caseRepository.saveAndFlush(legalCase);

		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("reason", reason);
		auditLogger.record(admin, AdminAuditAction.CASE_UNPUBLISH, AuditTargetType.CASE, caseId, detail, request);
		caseRepository.flush();
		return queryService.getCase(caseId);
	}

	private LegalCase lockCase(Long caseId) {
		return caseRepository.findByIdForUpdate(caseId).orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));
	}

	/** "valueAxis" 키는 있어야 한다(값 null은 허용 = 어느 축에도 맞지 않음). 다른 키 · 모르는 축은 거절 */
	private static ValueAxis valueAxis(Map<String, Object> body) {
		if (body == null || body.size() != 1 || !body.containsKey("valueAxis")) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "valueAxis");
		}
		Object value = body.get("valueAxis");
		if (value == null) {
			return null;
		}
		try {
			return ValueAxis.valueOf((String) value);
		} catch (ClassCastException | IllegalArgumentException e) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "valueAxis");
		}
	}
}
