package com.team2.project.legalcase.service;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.legalcase.domain.CaseIncidentPhoto;
import com.team2.project.legalcase.domain.CasePaper;
import com.team2.project.legalcase.domain.IncidentPhotoRole;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.dto.PublicCasePaperResponse;
import com.team2.project.legalcase.repository.CaseIncidentPhotoRepository;
import com.team2.project.legalcase.repository.CasePaperRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 신문 지면 조회 (API 17, BE-102). 신문 지면 자체는 BE-92(관리자 생성)가 만들고, 사진은
 * BE-91 · BE-101(역할 칸)이 관리하는 걸 그대로 가져다 조립만 한다 — 새로 저장하지 않는다
 * (BE-92 문서와 같은 원칙).
 */
@Service
@RequiredArgsConstructor
public class CasePaperQueryService {

	private final LegalCaseRepository legalCaseRepository;
	private final CasePaperRepository casePaperRepository;
	private final CaseIncidentPhotoRepository incidentPhotoRepository;

	@Transactional(readOnly = true)
	public PublicCasePaperResponse getCasePaper(Long caseId) {
		LegalCase legalCase = legalCaseRepository.findPublishedById(caseId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));
		CasePaper paper = casePaperRepository.findByLegalCaseId(caseId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PAPER_NOT_FOUND));

		CaseIncidentPhoto origin = incidentPhotoRepository.findByLegalCaseIdAndRole(caseId, IncidentPhotoRole.ORIGIN)
			.orElse(null);
		CaseIncidentPhoto conclusion = incidentPhotoRepository
			.findByLegalCaseIdAndRole(caseId, IncidentPhotoRole.CONCLUSION).orElse(null);

		return PublicCasePaperResponse.of(caseId, paper, legalCase.getThumbnailUrl(), origin, conclusion);
	}
}
