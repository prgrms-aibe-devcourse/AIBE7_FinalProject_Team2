package com.team2.project.experience.service;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.owner.Owner;
import com.team2.project.common.web.Requester;
import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 체험 찾기 (API 명세 1-3: 사건 ID + 회원 또는 익명 ID 쿠키, BE-140). API 3 ~ 15의 공통 진입점
 * 실패 순서: 비공개 · 없는 사건 → CASE_NOT_FOUND / 손님인데 쿠키 없음 · 모르는 익명 ID, 체험 없음 → EXPERIENCE_NOT_FOUND
 * 조회만 하므로 읽기 전용이다. 손님의 최근 접속 시각 갱신은 AnonymousUserRepository가 별도 트랜잭션으로 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyExperienceService {

	private final LegalCaseRepository legalCaseRepository;

	private final ExperienceRepository experienceRepository;

	private final OwnerService ownerService;

	/** 내 체험 (상태 조건 없음, API 3) */
	@Transactional(readOnly = true)
	public Experience getMyExperience(Long caseId, Requester requester) {
		if (legalCaseRepository.findPublishedById(caseId).isEmpty()) {
			throw new BusinessException(ErrorCode.CASE_NOT_FOUND);
		}
		Owner owner = ownerService.find(requester)
			.orElseThrow(() -> new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND));
		return experienceRepository.findLatest(owner, caseId)
			.orElseThrow(() -> new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND));
	}

	/** 내 체험 + 상태가 required 이상인지 확인 (결과 조회 API 10 · 12 · 14 · 15 등). 아니면 INVALID_STATE */
	@Transactional(readOnly = true)
	public Experience getMyExperienceAtLeast(Long caseId, Requester requester, ExperienceStatus required) {
		Experience experience = getMyExperience(caseId, requester);
		if (!experience.getStatus().isAtLeast(required)) {
			throw new InvalidExperienceStateException(experience.getStatus(), required + " 이후에 할 수 있는 요청입니다.");
		}
		return experience;
	}

	/** 내 체험 + 상태가 from ~ to 범위인지 확인 (API 4는 STARTED만, API 6 · 7은 PRE_JUDGED ~ REVIEWED 등). 아니면 INVALID_STATE */
	@Transactional(readOnly = true)
	public Experience getMyExperienceBetween(Long caseId, Requester requester,
		ExperienceStatus from, ExperienceStatus to) {
		Experience experience = getMyExperience(caseId, requester);
		ExperienceStatus status = experience.getStatus();
		if (!status.isAtLeast(from) || status.ordinal() > to.ordinal()) {
			throw new InvalidExperienceStateException(status, "지금 단계에서는 할 수 없는 요청입니다.");
		}
		return experience;
	}
}
