package com.team2.project.legalcase.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.experience.repository.CaseParticipantCount;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.legalcase.domain.CaseScript;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.dto.CaseDetailResponse;
import com.team2.project.legalcase.dto.CaseListResponse;
import com.team2.project.legalcase.dto.CaseListResponse.CaseItem;
import com.team2.project.legalcase.dto.CaseListResponse.MyStatus;
import com.team2.project.legalcase.dto.CaseListResponse.Summary;
import com.team2.project.legalcase.repository.CaseScriptRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalCaseService {

	private final LegalCaseRepository legalCaseRepository;
	private final ExperienceRepository experienceRepository;
	private final CaseScriptRepository scriptRepository;

	/**
	 * 공개된 사건 목록. MVP는 사건이 몇 개 안 되므로 공개 사건을 한 번에 읽어
	 * summary(전체 기준)와 필터 결과를 메모리에서 만든다.
	 *
	 * @param anonymousUserId 익명 ID 쿠키 값. 있으면 사건마다 내 진행 상태(myStatus)를 붙인다. 쿠키를 새로 발급하지는 않는다
	 */
	@Transactional(readOnly = true)
	public CaseListResponse getCases(CrimeType crimeType, Optional<UUID> anonymousUserId) {
		List<LegalCase> published = legalCaseRepository.findAllPublished();
		Map<Long, Long> participantCounts = experienceRepository.countCompletedFirstAttemptsByCase().stream()
				.collect(Collectors.toMap(CaseParticipantCount::getCaseId, CaseParticipantCount::getParticipantCount));
		Map<Long, MyStatus> myStatuses = myStatuses(anonymousUserId);
		Set<Long> scriptReadyCaseIds = scriptRepository.findApprovedCaseIds();	// 사건마다 조회하지 않고 한 번에

		List<CaseItem> items = published.stream()
				.filter(legalCase -> crimeType == null || legalCase.getCrimeType() == crimeType)
				.map(legalCase -> CaseItem.of(legalCase, participantCounts.getOrDefault(legalCase.getId(), 0L),
						scriptReadyCaseIds.contains(legalCase.getId()), myStatuses.get(legalCase.getId())))
				.toList();
		return new CaseListResponse(summarize(published), items);
	}

	/**
	 * API 16. 공개 사건 상세 (S-20 사건 디테일, BE-74). 체험 없이 부를 수 있다.
	 * 진행 상태(myStatus)는 API 1과 같은 기준(최신 회차)이고, 쿠키는 읽기만 한다(발급은 API 2).
	 * 화면은 "이어서 하기"에서 API 2를 불러 정확한 단계로 간다(세부 상태는 내보내지 않는다).
	 *
	 * @throws BusinessException CASE_NOT_FOUND — 사건 없음 · 비공개(구분하지 않는다)
	 */
	@Transactional(readOnly = true)
	public CaseDetailResponse getCase(Long caseId, Optional<UUID> anonymousUserId) {
		LegalCase legalCase = legalCaseRepository.findPublishedById(caseId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));
		MyStatus myStatus = anonymousUserId
				.flatMap(id -> experienceRepository.findLatest(id, caseId))
				.map(experience -> MyStatus.from(experience.getStatus()))
				.orElse(null);
		boolean scriptReady = scriptRepository.findByLegalCaseId(caseId).filter(CaseScript::isPublic).isPresent();	// API 18과 같은 조건
		return CaseDetailResponse.of(legalCase, experienceRepository.countCompletedFirstAttempts(caseId), scriptReady,
				myStatus);
	}

	/**
	 * 사건별 내 진행 상태 (FE-19 배지). 쿠키가 없으면(첫 방문) 조회하지 않는다.
	 * 기준은 API 2 · 3과 같은 "사건마다 가장 최근 회차"라, 배지와 체험 시작 후 이동하는 화면이 어긋나지 않는다.
	 * 마이페이지와 같은 쿼리(findLatestPerCase)를 한 번만 불러 사건 수만큼 조회하지 않는다.
	 */
	private Map<Long, MyStatus> myStatuses(Optional<UUID> anonymousUserId) {
		return anonymousUserId
				.map(id -> experienceRepository.findLatestPerCase(id).stream()
						.collect(Collectors.toMap(experience -> experience.getLegalCase().getId(),
								experience -> MyStatus.from(experience.getStatus()))))
				.orElseGet(Map::of);
	}

	/** 공개(PUBLISHED)된 사건이 아니면 CASE_NOT_FOUND. 사건 없음과 비공개를 구분하지 않는다. */
	@Transactional(readOnly = true)
	public void requirePublished(Long caseId) {
		if (!legalCaseRepository.existsPublishedById(caseId)) {
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
