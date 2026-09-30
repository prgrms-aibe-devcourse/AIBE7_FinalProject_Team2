package com.team2.project.legalcase.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.experience.repository.CaseParticipantCount;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.legalcase.domain.CaseStatus;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.dto.CaseListResponse;
import com.team2.project.legalcase.dto.CaseListResponse.CaseItem;
import com.team2.project.legalcase.dto.CaseListResponse.Summary;
import com.team2.project.legalcase.repository.LegalCaseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LegalCaseService {

	private final LegalCaseRepository legalCaseRepository;
	private final ExperienceRepository experienceRepository;

	/**
	 * 공개된 사건 목록. MVP는 사건이 몇 개 안 되므로 공개 사건을 한 번에 읽어
	 * summary(전체 기준)와 필터 결과를 메모리에서 만든다.
	 */
	@Transactional(readOnly = true)
	public CaseListResponse getCases(CrimeType crimeType) {
		List<LegalCase> published = legalCaseRepository.findByStatusLatestFirst(CaseStatus.PUBLISHED);
		Map<Long, Long> participantCounts = experienceRepository.countCompletedFirstAttempts().stream()
				.collect(Collectors.toMap(CaseParticipantCount::getCaseId, CaseParticipantCount::getParticipantCount));

		List<CaseItem> items = published.stream()
				.filter(legalCase -> crimeType == null || legalCase.getCrimeType() == crimeType)
				.map(legalCase -> CaseItem.of(legalCase, participantCounts.getOrDefault(legalCase.getId(), 0L)))
				.toList();
		return new CaseListResponse(summarize(published), items);
	}

	/** 공개(PUBLISHED)된 사건이 아니면 CASE_NOT_FOUND. 사건 없음과 비공개를 구분하지 않는다. */
	@Transactional(readOnly = true)
	public void requirePublished(Long caseId) {
		if (!legalCaseRepository.existsByIdAndStatus(caseId, CaseStatus.PUBLISHED)) {
			throw new BusinessException(ErrorCode.CASE_NOT_FOUND);
		}
	}

	private Summary summarize(List<LegalCase> published) {
		Map<CrimeType, Integer> byCrimeType = new EnumMap<>(CrimeType.class);
		for (CrimeType type : CrimeType.values()) {
			byCrimeType.put(type, 0);
		}
		published.forEach(legalCase -> byCrimeType.merge(legalCase.getCrimeType(), 1, Integer::sum));
		return new Summary(published.size(), byCrimeType);
	}
}
