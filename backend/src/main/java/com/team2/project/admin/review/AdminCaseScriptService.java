package com.team2.project.admin.review;

import com.team2.project.admin.audit.AdminAuditAction;
import com.team2.project.admin.audit.AdminAuditLogger;
import com.team2.project.admin.audit.AuditTargetType;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.admin.review.dto.CaseScriptResponse;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.legalcase.domain.CasePaper;
import com.team2.project.legalcase.domain.CaseScript;
import com.team2.project.legalcase.domain.CaseScriptBlock;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.ScriptUnit;
import com.team2.project.legalcase.repository.CasePaperRepository;
import com.team2.project.legalcase.repository.CaseScriptRepository;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
	private final CasePaperRepository paperRepository;
	private final AdminAccountRepository accountRepository;
	private final AdminAuditLogger auditLogger;

	@Transactional(readOnly = true)
	public CaseScriptResponse getScript(Long caseId) {
		return response(caseId, findScript(caseId));
	}

	/**
	 * 승인 = 이 대본을 공개한다 (A-13-11). 1차는 <b>통승인</b>이다 — 모든 블록을 한 번에 승인한다(BE-126).
	 * 승인할 수 없는 대본({@link CaseScript#isApprovable()})은 거절한다 — 증거 카드와 대사가 1:1로 맞지 않거나
	 * (BE-107 PR #277 리뷰 7번), 빠진 단위 · 검수 조건 오류가 있으면 체험이 깨진다. 증거조사만 만든 대본도 승인할 수 없다(준비 중).
	 * 만든 뒤 사건 내용 · 기사가 바뀐 블록이 있어도 거절한다(PR #367 리뷰 2번).
	 */
	@Transactional
	public CaseScriptResponse approve(Long caseId, AdminPrincipal admin, HttpServletRequest request) {
		CaseScript script = lockAndFindScript(caseId);
		if (!script.isApprovable()) {
			throw new BusinessException(ErrorCode.INVALID_STATE,
				"만들지 못한 대사 · 단위나 검수 오류가 있는 대본은 승인할 수 없습니다. 대본을 다시 생성해 주세요.");
		}
		// 만든 뒤 사건 내용 · 기사 · 민감 플래그가 바뀐 블록은 공개하지 않는다(BlockStaleness.BLOCKING) — 재생성하거나 "확인했음"으로 푼다
		guardNotBlockingStale(script, script.blockingStale(script.getLegalCase(), paperRevision(caseId)));
		script.approve(admin.adminId(), Instant.now());
		auditLogger.record(admin, AdminAuditAction.CASE_SCRIPT_APPROVE, AuditTargetType.CASE, caseId, Map.of(),
			request);
		return response(caseId, script);
	}

	/**
	 * 반려 (A-13-12). 1차는 <b>통거절</b>이다 — 모든 블록을 한 번에 반려한다(BE-126). 반려한 대본은 공개되지 않고
	 * (BE-109 서빙 기준), 블록 단위로 고칠 수 있게 되기(2차) 전까지는 다시 등록해 새로 만든다.
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
	 * <p>대사는 U3 블록에 있다(BE-126). 고치면 모든 블록의 검수 기록이 지워져 <b>다시 검수 대기</b>가 된다. 빈 대사를
	 * 사람이 채워 모든 줄이 차면 {@code publishable}도 다시 계산돼 승인할 수 있게 된다.
	 */
	@Transactional
	public CaseScriptResponse editLine(Long caseId, int factorId, String text, AdminPrincipal admin,
			HttpServletRequest request) {
		CaseScript script = lockAndFindScript(caseId);
		guardNotPublished(script);
		if (!script.editLine(factorId, text, Instant.now())) {
			throw new BusinessException(ErrorCode.SCRIPT_LINE_NOT_FOUND);
		}
		// 고친 대사 본문은 감사 로그에 남기지 않는다 — 어느 요소를 고쳤는지만 남긴다(원문 · 모델 출력 취급)
		auditLogger.record(admin, AdminAuditAction.CASE_SCRIPT_UPDATE, AuditTargetType.CASE, caseId,
			Map.<String, Object>of("factorId", factorId), request);
		return response(caseId, script);
	}

	/**
	 * 블록 하나 승인 (A-13-18, BE-126 2차). 처음 검수는 통승인 · 통거절이고(화면 흐름), 반려한 뒤 · 재생성한 뒤에는 블록마다 승인한다.
	 * 마지막 블록이 승인되면 대본이 공개된다(다섯 단위 모두 승인). 오류 있는 블록, 대본 전체 검수 오류가 남은 대본, 승인을 막는 낡음이
	 * 있는 블록은 승인할 수 없다.
	 */
	@Transactional
	public CaseScriptResponse approveBlock(Long caseId, ScriptUnit unit, AdminPrincipal admin, HttpServletRequest request) {
		CaseScript script = lockAndFindScript(caseId);
		CaseScriptBlock block = findBlock(script, unit);
		if (!script.canApproveBlocks()) {
			// 통승인(A-13-11)과 같은 대본 조건 — 블록 하나씩 승인해 공개하는 길로 우회하지 않게 한다(PR #383 CodeRabbit 리뷰)
			throw new BusinessException(ErrorCode.INVALID_STATE,
				"만들지 못한 단위 · 기사가 있거나 생성이 중간에 멈춘 대본은 블록을 승인할 수 없습니다. 다시 생성해 주세요.");
		}
		if (!block.isPublishable()) {
			throw new BusinessException(ErrorCode.INVALID_STATE,
				"검수 오류가 있는 블록은 승인할 수 없습니다. 고치거나 다시 생성해 주세요.");
		}
		guardNotBlockingStale(script, script.blockingStale(script.getLegalCase(), paperRevision(caseId)).stream()
			.filter(unit::equals).toList());
		script.approveBlock(unit, admin.adminId(), Instant.now());
		auditLogger.record(admin, AdminAuditAction.CASE_SCRIPT_APPROVE, AuditTargetType.CASE, caseId,
			Map.<String, Object>of("unit", unit.name()), request);
		return response(caseId, script);
	}

	/** 블록 하나 반려 (A-13-19, BE-126 2차). 반려한 블록이 있으면 대본은 공개되지 않는다 */
	@Transactional
	public CaseScriptResponse rejectBlock(Long caseId, ScriptUnit unit, String reason, AdminPrincipal admin,
			HttpServletRequest request) {
		CaseScript script = lockAndFindScript(caseId);
		findBlock(script, unit);
		guardNotPublished(script);
		script.rejectBlock(unit, admin.adminId(), Instant.now(), reason);
		auditLogger.record(admin, AdminAuditAction.CASE_SCRIPT_REJECT, AuditTargetType.CASE, caseId,
			Map.<String, Object>of("unit", unit.name()), request);
		return response(caseId, script);
	}

	/**
	 * "확인했음" (A-13-20, BE-126 2차). 낡았지만 다시 만들지 않고 그대로 두기로 한 블록의 근거를 지금 값으로 맞춘다 — 낡음 표시가
	 * 사라지고, 승인을 막던 낡음(사건 내용 · 기사 · 민감 플래그)도 풀린다. 내용 · 검수 상태는 그대로다. 무엇을 확인했는지 감사 로그에 남긴다
	 */
	@Transactional
	public CaseScriptResponse acknowledgeStale(Long caseId, ScriptUnit unit, AdminPrincipal admin,
			HttpServletRequest request) {
		CaseScript script = lockAndFindScript(caseId);
		findBlock(script, unit);
		List<String> added = script.addedSensitiveFlags(unit, script.getLegalCase());
		if (!added.isEmpty()) {
			// 새로 생긴 플래그의 가드 문구 없이 만든 블록이다 — 확인만으로 공개하면 막은 이유가 사라진다(PR #383 리뷰 2번)
			throw new BusinessException(ErrorCode.INVALID_STATE, "새로 생긴 민감 플래그(" + String.join(" · ", added)
				+ ")의 가드 없이 만든 블록이라 확인했음으로 둘 수 없습니다. 다시 생성해 주세요.");
		}
		List<String> acknowledged = script.acknowledgeStale(unit, script.getLegalCase(), paperRevision(caseId));
		if (!acknowledged.isEmpty()) {
			Map<String, Object> detail = new LinkedHashMap<>();
			detail.put("unit", unit.name());
			detail.put("acknowledgedStale", acknowledged);
			auditLogger.record(admin, AdminAuditAction.CASE_SCRIPT_UPDATE, AuditTargetType.CASE, caseId, detail, request);
		}
		return response(caseId, script);
	}

	/**
	 * 블록 문자열 한 줄 직접 수정 (A-13-21, BE-126 2차, U0 · U1 · U2 · U4). 자리는 검수 결과의 {@code path}(JSON Pointer)다. 그 블록만
	 * 다시 검수 대기가 되고 판이 올라 뒤 블록이 낡음으로 보인다. U0를 고치면 혐의 인정 여부를 다시 계산한다. 고친 문장은 감사 로그에
	 * 남기지 않는다(A-13-13과 같다)
	 */
	@Transactional
	public CaseScriptResponse editText(Long caseId, ScriptUnit unit, String path, String text, AdminPrincipal admin,
			HttpServletRequest request) {
		if (unit == ScriptUnit.U3) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "증거조사 대사(U3)는 대사 수정(A-13-13)으로 고칩니다.");
		}
		CaseScript script = lockAndFindScript(caseId);
		findBlock(script, unit);
		guardNotPublished(script);
		if (!script.editText(unit, path, text)) {
			throw new BusinessException(ErrorCode.SCRIPT_LINE_NOT_FOUND, "블록에 그 자리의 문장이 없습니다: " + path);
		}
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("unit", unit.name());
		detail.put("path", path);
		auditLogger.record(admin, AdminAuditAction.CASE_SCRIPT_UPDATE, AuditTargetType.CASE, caseId, detail, request);
		return response(caseId, script);
	}

	/**
	 * 점검 상태(BE-126 2차-2, API 18 503) 전까지: 공개 중인 사건의 대본은 <b>승인을 지우는</b> 수정 · 블록 반려를 받지 않는다 — 승인이
	 * 풀리는 순간 체험 중인 사용자의 API 18이 404가 된다(PR #383 리뷰 1번). 블록 승인 · 확인했음은 대본을 내리지 않아 막지 않고,
	 * 통거절(A-13-12)은 잘못 승인한 대본을 내리는 길이라 일부러 둔다
	 */
	static void guardNotPublished(CaseScript script) {
		if (script.getLegalCase().isPublished()) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "공개 중인 사건의 대본은 지금 고치거나 블록을 반려할 수 없습니다 — "
				+ "승인이 풀려 체험 중인 사용자에게 대본이 사라집니다. 공개를 취소(A-13-14)한 뒤 고쳐 주세요.");
		}
	}

	private static CaseScriptBlock findBlock(CaseScript script, ScriptUnit unit) {
		return script.block(unit).orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_BLOCK_NOT_FOUND));
	}

	private static void guardNotBlockingStale(CaseScript script, List<ScriptUnit> stale) {
		if (!stale.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "대본을 만든 뒤 사건 내용 · 기사 · 민감 플래그가 바뀌어 "
				+ stale.stream().map(ScriptUnit::name).collect(Collectors.joining(" · "))
				+ " 블록이 옛 값을 보고 만들어졌습니다. 다시 생성하거나 확인했음으로 표시해 주세요.");
		}
	}

	/**
	 * 사건 내용 · 민감 플래그(A-11) · 기사(A-13-6 · 8)를 고친 뒤, 승인을 막는 낡음이 생긴 블록의 승인을 지운다(PR #367 리뷰). 부르는 쪽이 사건 행을 잠근
	 * 트랜잭션 안에서 부른다 — 승인(A-13-11)도 같은 행을 잠그므로 둘이 엇갈리지 않는다. 판이 그대로면(같은 값으로 고친 경우)
	 * 아무것도 지우지 않는다.
	 *
	 * <p>공개한 사건은 두 경로 모두 고칠 수 없어, 이 처리로 공개 중인 사건의 대본이 내려가는 일은 없다. 승인한 대본이 있는
	 * 초안 사건을 고치면 공개 전에 대본이 다시 검수 대기가 된다.
	 *
	 * @return 승인을 지운 단위. 대본이 없으면 빈 목록
	 */
	@Transactional
	public List<ScriptUnit> revokeStaleApprovals(LegalCase legalCase) {
		return scriptRepository.findByLegalCaseId(legalCase.getId())
			.map(script -> script.revokeStaleApprovals(legalCase, paperRevision(legalCase.getId())))
			.orElse(List.of());
	}

	/**
	 * 승인을 지웠으면 감사 로그에 어느 단위인지 남긴다 — 관리자가 고친 것은 사건 내용 · 기사지만 공개될 대본의 상태도 바뀌었기
	 * 때문이다. 고친 쪽(A-11 · A-13-6 · 8)의 감사 기록에 붙인다
	 */
	static Map<String, Object> withRevoked(Map<String, Object> detail, List<ScriptUnit> revoked) {
		if (!revoked.isEmpty()) {
			detail.put("scriptApprovalRevoked", revoked.stream().map(ScriptUnit::name).toList());
		}
		return detail;
	}

	private Integer paperRevision(Long caseId) {
		return paperRepository.findByLegalCaseId(caseId).map(CasePaper::getRevision).orElse(null);
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
		Map<Long, String> emails = new HashMap<>();
		return CaseScriptResponse.of(script, labels,
			adminId -> adminId == null ? null : emails.computeIfAbsent(adminId, this::reviewerEmail),
			script.staleness(script.getLegalCase(), paperRevision(caseId)));
	}

	/**
	 * 검수자는 A-8과 같이 이메일로 내려준다. 계정이 지워졌으면 비운다(감사 로그가 이메일을 따로 보관한다). 블록마다 검수자가
	 * 있어 같은 계정을 여러 번 찾지 않게 위에서 모아 둔다
	 */
	private String reviewerEmail(Long adminId) {
		return accountRepository.findById(adminId).map(account -> account.getEmail()).orElse(null);
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
