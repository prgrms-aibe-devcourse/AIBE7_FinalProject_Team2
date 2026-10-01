package com.team2.project.experience.service;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.repository.AnonymousUserRepository;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;

import lombok.RequiredArgsConstructor;

/**
 * 체험 INSERT만 담당하는 별도 빈. 유니크 제약 위반이 나면 이 트랜잭션은 롤백되므로,
 * 호출하는 쪽(ExperienceService)이 트랜잭션 밖에서 예외를 받아 새 트랜잭션으로 기존 체험을 다시 읽는다.
 */
@Component
@RequiredArgsConstructor
class ExperienceWriter {

	private final ExperienceRepository experienceRepository;
	private final AnonymousUserRepository anonymousUserRepository;
	private final LegalCaseRepository legalCaseRepository;

	@Transactional
	public Experience create(UUID anonymousUserId, Long caseId) {
		// 사용자 · 사건은 호출 전에 확인했으므로 조회 없이 참조만 걸어 FK 값으로 INSERT한다
		Experience experience = Experience.start(
				anonymousUserRepository.getReferenceById(anonymousUserId),
				legalCaseRepository.getReferenceById(caseId));
		// flush까지 여기서 끝내 제약 위반이 이 메서드 안에서 바로 드러나게 한다
		return experienceRepository.saveAndFlush(experience);
	}
}
