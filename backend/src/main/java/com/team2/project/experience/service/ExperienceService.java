package com.team2.project.experience.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.dto.ExperienceResponse;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.legalcase.service.LegalCaseService;

import lombok.RequiredArgsConstructor;

/**
 * API 2(체험 시작)의 트랜잭션은 각 협력 빈(AnonymousUserService, ExperienceWriter, Repository)이 따로 잡는다.
 * 체험 생성이 유니크 제약에 걸려도 그 트랜잭션만 롤백되고, 여기서 새 트랜잭션으로 기존 체험을 읽을 수 있다.
 */
@Service
@RequiredArgsConstructor
public class ExperienceService {

	private final LegalCaseService legalCaseService;
	private final AnonymousUserService anonymousUserService;
	private final MyExperienceService myExperienceService;
	private final ExperienceWriter experienceWriter;
	private final ExperienceRepository experienceRepository;

	/** API 2. 체험 시작. 이미 있으면 새로 만들지 않고 기존 체험을 돌려준다. */
	public StartResult start(Long caseId, Optional<UUID> anonymousId) {
		legalCaseService.requirePublished(caseId);
		AnonymousUserService.IssuedAnonymousUser issued = anonymousUserService.getOrIssue(anonymousId);
		UUID userId = issued.user().getId();
		UUID issuedCookieId = issued.issued() ? userId : null;

		Optional<Experience> existing = experienceRepository.findLatest(userId, caseId);
		if (existing.isPresent()) {
			return new StartResult(ExperienceResponse.from(existing.get()), false, issuedCookieId);
		}
		try {
			Experience created = experienceWriter.create(userId, caseId);
			return new StartResult(ExperienceResponse.from(created), true, issuedCookieId);
		} catch (DataIntegrityViolationException e) {
			// 동시에 시작한 다른 요청이 먼저 만들었다: 기존 체험을 다시 읽어 그대로 돌려준다
			Experience winner = experienceRepository.findLatest(userId, caseId).orElseThrow(() -> e);
			return new StartResult(ExperienceResponse.from(winner), false, issuedCookieId);
		}
	}

	/** API 3. 내 체험 상태 (사건 · 쿠키 · 체험 확인은 MyExperienceService, DTO 변환까지 읽기 전용 트랜잭션 안에서) */
	@Transactional(readOnly = true)
	public ExperienceResponse getMyExperience(Long caseId, Optional<UUID> anonymousId) {
		return ExperienceResponse.from(myExperienceService.getMyExperience(caseId, anonymousId));
	}

	/**
	 * @param created 새로 만들었으면 true (201), 기존 체험이면 false (200)
	 * @param issuedCookieId 익명 ID를 새로 발급했을 때만 값이 있다 (Set-Cookie 대상)
	 */
	public record StartResult(ExperienceResponse experience, boolean created, UUID issuedCookieId) {
	}
}
