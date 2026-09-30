package com.team2.project.experience.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.repository.ExperienceRepository;

import lombok.RequiredArgsConstructor;

/**
 * 체험 INSERT만 담당하는 별도 빈. 유니크 제약 위반이 나면 이 트랜잭션은 롤백되므로,
 * 호출하는 쪽(ExperienceService)이 트랜잭션 밖에서 예외를 받아 새 트랜잭션으로 기존 체험을 다시 읽는다.
 */
@Component
@RequiredArgsConstructor
class ExperienceWriter {

	private final ExperienceRepository experienceRepository;

	@Transactional
	public Experience create(UUID anonymousUserId, Long caseId) {
		// flush까지 여기서 끝내 제약 위반이 이 메서드 안에서 바로 드러나게 한다
		return experienceRepository.saveAndFlush(Experience.start(anonymousUserId, caseId, OffsetDateTime.now()));
	}
}
