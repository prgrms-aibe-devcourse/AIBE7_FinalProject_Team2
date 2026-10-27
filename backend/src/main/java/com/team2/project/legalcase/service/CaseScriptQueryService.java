package com.team2.project.legalcase.service;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.legalcase.domain.CaseScript;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.dto.PublicCaseScriptResponse;
import com.team2.project.legalcase.repository.CaseScriptRepository;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 사건 대본 조회 (API 18, BE-109). BE-108이 적재 · 검수한 대본을 그대로 가져다 공개용으로 걸러만
 * 준다 — 새로 저장하지 않는다(신문 지면 {@link CasePaperQueryService}와 같은 원칙).
 *
 * <p>승인한 대본만 내려준다(A-13-11, {@link CaseScript#isPublic()}). <b>사건 공개와 대본 공개는
 * 별개다</b> — 사건이 {@code PUBLISHED}여도 대본이 승인 전이면 내려가지 않는다. 비공개 사건은
 * {@code CASE_NOT_FOUND}, 대본이 없거나 아직 승인 전이면 {@code SCRIPT_NOT_FOUND}다(해송님 동의,
 * BE-109 Jira 댓글, 2026-10-27) — API 17 신문 지면의 {@code PAPER_NOT_FOUND}와 같은 모양이라
 * 프론트는 404를 "대본 없음"으로 보고 MVP 화면으로 가면 된다. 빈 배열 대신 404를 쓰는 이유는, 빈
 * 배열이면 대본이 없는 것인지 대사가 0줄인 것인지 구분할 수 없기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class CaseScriptQueryService {

	private final LegalCaseRepository legalCaseRepository;
	private final CaseScriptRepository scriptRepository;
	private final FactorRepository factorRepository;

	@Transactional(readOnly = true)
	public PublicCaseScriptResponse getCaseScript(Long caseId) {
		legalCaseRepository.findPublishedById(caseId).orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));
		CaseScript script = scriptRepository.findByLegalCaseId(caseId).filter(CaseScript::isPublic)
			.orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));

		return PublicCaseScriptResponse.of(script, labelsByFactorId(caseId));
	}

	/**
	 * 대사마다 "어느 사실에 대한 것인지"(판단 요소 문구, = {@code card})를 붙이려고 판단 요소를 찾는다.
	 * 파이프라인 {@code factorId}는 적재할 때 {@code factor.display_order}로 들어간다(BE-93 2차) —
	 * 그 번호로 맞춘다(admin 쪽 {@code AdminCaseScriptService.response}와 같은 방식).
	 */
	private Map<Integer, String> labelsByFactorId(Long caseId) {
		Map<Integer, String> labels = new LinkedHashMap<>();
		for (Factor factor : factorRepository.findAllByCaseId(caseId)) {
			labels.put(factor.getDisplayOrder(), factor.getLabel());
		}
		return labels;
	}
}
