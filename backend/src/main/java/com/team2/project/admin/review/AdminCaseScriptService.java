package com.team2.project.admin.review;

import com.team2.project.admin.audit.AdminAuditAction;
import com.team2.project.admin.audit.AdminAuditLogger;
import com.team2.project.admin.audit.AuditTargetType;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.admin.review.dto.CaseScriptResponse;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.legalcase.domain.CaseScript;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.repository.CaseScriptRepository;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사건 대본 검수 (BE-108, A-13-10 · A-13-11 · A-13-12). 적재된 대본을 관리자가 보고 승인 · 반려한다.
 *
 * <p><b>사건 공개와는 별개다.</b> 공개된 사건이어도 대본은 승인 전까지 내려가지 않고(BE-109), 공개된 뒤에
 * 대본만 늦게 들어와 승인될 수도 있다. 그래서 신문 지면(A-13-6 ~ 8)과 달리 "공개한 사건은 못 고친다"는
 * 제약을 두지 않는다 — 대본은 사건 내용이 아니라 체험 연출이기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class AdminCaseScriptService {

	private final LegalCaseRepository caseRepository;
	private final CaseScriptRepository scriptRepository;
	private final FactorRepository factorRepository;
	private final AdminAccountRepository accountRepository;
	private final AdminAuditLogger auditLogger;

	@Transactional(readOnly = true)
	public CaseScriptResponse getScript(Long caseId) {
		return response(caseId, findScript(caseId));
	}

	/**
	 * 승인 = 이 대본을 공개한다 (A-13-11). 파이프라인이 어긋난다고 표시한 대본({@code publishable: false})은
	 * 승인할 수 없다 — 증거 카드와 대사가 1:1로 맞지 않는 상태라 체험이 깨진다(BE-107 PR #277 리뷰 7번).
	 */
	@Transactional
	public CaseScriptResponse approve(Long caseId, AdminPrincipal admin, HttpServletRequest request) {
		CaseScript script = lockAndFindScript(caseId);
		if (!script.isPublishable()) {
			throw new BusinessException(ErrorCode.INVALID_STATE,
				"증거 카드와 대사가 어긋난 대본은 승인할 수 없습니다. 대본을 다시 생성해 주세요.");
		}
		script.approve(admin.adminId(), Instant.now());
		auditLogger.record(admin, AdminAuditAction.CASE_SCRIPT_APPROVE, AuditTargetType.CASE, caseId, Map.of(),
			request);
		return response(caseId, script);
	}

	/**
	 * 반려 (A-13-12). 반려한 대본은 공개되지 않는다(BE-109 서빙 기준).
	 *
	 * <p>승인한 대본도 반려로 내릴 수 있다 — 판결 후보(A-10)는 교체할 다른 후보가 있어 공개 중인 것을 막지만,
	 * 대본은 사건마다 하나뿐이라 막으면 잘못 승인한 대본을 되돌릴 방법이 없어진다.
	 */
	@Transactional
	public CaseScriptResponse reject(Long caseId, String reason, AdminPrincipal admin, HttpServletRequest request) {
		CaseScript script = lockAndFindScript(caseId);
		script.reject(admin.adminId(), Instant.now(), reason);
		auditLogger.record(admin, AdminAuditAction.CASE_SCRIPT_REJECT, AuditTargetType.CASE, caseId, Map.of(),
			request);
		return response(caseId, script);
	}

	/**
	 * 대사 한 줄 직접 수정 (A-13-13). 숫자 하나 · 말투 때문에 모델을 수십 번 다시 부르는 것보다 싸다
	 * (해송님 요청, 2026-10-27). 신문 지면 수정 A-13-8과 같은 자리의 기능이다.
	 *
	 * <p>{@code factorId}는 파이프라인 번호다(= {@code factor.display_order}). 대사는 판단 요소마다 한 줄이고
	 * 어느 측이 말하는지는 요소의 방향에서 정해지므로(`script_generation.py`), 이 번호 하나로 줄이 정해진다.
	 *
	 * <p>고치면 검수 기록이 지워져 <b>다시 검수 대기</b>가 된다. 빈 대사를 사람이 채워 모든 줄이 차면
	 * {@code publishable}도 다시 계산돼 승인할 수 있게 된다.
	 */
	@Transactional
	public CaseScriptResponse editLine(Long caseId, int factorId, String text, AdminPrincipal admin,
			HttpServletRequest request) {
		CaseScript script = lockAndFindScript(caseId);
		if (!script.editLine(factorId, text, Instant.now())) {
			throw new BusinessException(ErrorCode.SCRIPT_LINE_NOT_FOUND);
		}
		// 고친 대사 본문은 감사 로그에 남기지 않는다 — 어느 요소를 고쳤는지만 남긴다(원문 · 모델 출력 취급)
		auditLogger.record(admin, AdminAuditAction.CASE_SCRIPT_UPDATE, AuditTargetType.CASE, caseId,
			Map.<String, Object>of("factorId", factorId), request);
		return response(caseId, script);
	}

	/**
	 * 대사마다 "어느 사실에 대한 것인지"(판단 요소 문구)를 붙여 돌려준다. 파이프라인의 {@code factorId}는
	 * 적재할 때 {@code factor.display_order}로 들어가므로(BE-93 2차) 그 번호로 맞춘다 — 화면이 이 연결을
	 * 다시 하지 않아도 되게 한다(FE 요청, 2026-10-27).
	 */
	private CaseScriptResponse response(Long caseId, CaseScript script) {
		Map<Integer, String> labels = new LinkedHashMap<>();
		for (Factor factor : factorRepository.findAllByCaseId(caseId)) {
			labels.put(factor.getDisplayOrder(), factor.getLabel());
		}
		return CaseScriptResponse.of(script, labels, reviewerEmail(script));
	}

	/** 검수자는 A-8과 같이 이메일로 내려준다. 계정이 지워졌으면 비운다(감사 로그가 이메일을 따로 보관한다) */
	private String reviewerEmail(CaseScript script) {
		Long adminId = script.getReviewedByAdminId();
		return adminId == null ? null
			: accountRepository.findById(adminId).map(account -> account.getEmail()).orElse(null);
	}

	/** 사건이 없으면 CASE_NOT_FOUND, 사건은 있는데 대본이 없으면 SCRIPT_NOT_FOUND (신문 지면 A-13-7과 같은 구분) */
	private CaseScript findScript(Long caseId) {
		if (!caseRepository.existsById(caseId)) {
			throw new BusinessException(ErrorCode.CASE_NOT_FOUND);
		}
		return scriptRepository.findByLegalCaseId(caseId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
	}

	/**
	 * 상태를 바꾸는 요청(승인 · 반려 · 대사 수정)은 <b>사건 행을 먼저 잠그고</b> 대본을 읽는다 —
	 * A-9 · A-10이 쓰는 방식과 같다(`LegalCaseRepository.findByIdForUpdate`).
	 *
	 * <p>잠그지 않으면 이 PR이 세운 계약이 깨진다(PR #300 리뷰 3번): 관리자 A가 승인하려고 대본을 읽은 사이
	 * 관리자 B가 대사를 고쳐 커밋하면, A가 커밋할 때 <b>A가 읽었던 옛 대사가 되살아나면서 APPROVED까지
	 * 남는다</b>(Hibernate는 기본으로 행의 모든 칸을 다시 쓴다). 곧 검수하지 않은 대사가 공개된다.
	 * 대본 행이 아니라 사건 행을 잠그는 이유는, 같은 사건의 판결 후보 승인(A-9)과도 차례가 지켜지기 때문이다.
	 */
	private CaseScript lockAndFindScript(Long caseId) {
		caseRepository.findByIdForUpdate(caseId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));
		return scriptRepository.findByLegalCaseId(caseId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
	}
}
