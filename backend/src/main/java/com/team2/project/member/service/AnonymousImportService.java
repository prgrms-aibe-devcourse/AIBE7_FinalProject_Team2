package com.team2.project.member.service;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.experience.domain.AnonymousUser;
import com.team2.project.experience.repository.AnonymousUserRepository;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.judgment.repository.JudgmentRepository;
import com.team2.project.member.domain.MemberAnonymousImport;
import com.team2.project.member.domain.MemberAnonymousImport.Decision;
import com.team2.project.member.dto.AnonymousImportCandidateResponse;
import com.team2.project.member.dto.AnonymousImportResponse;
import com.team2.project.member.repository.MemberAnonymousImportRepository;
import com.team2.project.member.repository.MemberRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첫 로그인 가져오기 (확장 단계, BE-140, API 명세 M-9 ~ M-11, docs/decisions/BE-140.md).
 *
 * 후보는 이 브라우저 쿠키의 익명 ID 하나뿐이다. 이 회원에 연결돼 있고, 체험이 있고, 회원 체험이 0건이고, 결정 기록이 없을 때만 후보다.
 * 연결을 확인하므로 다른 회원의 익명 ID는 옮기지 못한다(공용 PC).
 *
 * M-10 · M-11은 회원 행을 잠가 같은 회원의 요청을 차례로 처리한다. 같은 결정을 다시 보내면(응답을 못 받은 재시도) 성공으로 답하고,
 * 후보가 아니면 409 ANONYMOUS_IMPORT_NOT_AVAILABLE이다
 */
@Service
@RequiredArgsConstructor
public class AnonymousImportService {

	private final MemberRepository memberRepository;
	private final MemberAnonymousImportRepository importRepository;
	private final AnonymousUserRepository anonymousUserRepository;
	private final ExperienceRepository experienceRepository;
	private final JudgmentRepository judgmentRepository;

	@Transactional(readOnly = true)
	public AnonymousImportCandidateResponse candidate(long memberId, Optional<UUID> cookie) {
		return candidateId(memberId, cookie)
			.map(id -> new AnonymousImportCandidateResponse(experienceRepository.countByAnonymousUser_Id(id),
				judgmentRepository.countPreJudgments(id)))
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

	@Transactional
	public AnonymousImportResponse importExperiences(long memberId, Optional<UUID> cookie) {
		lockMember(memberId);
		Optional<MemberAnonymousImport> decided = importRepository.findById(memberId);
		if (decided.isPresent()) {
			requireDecision(decided.get(), Decision.IMPORTED);
			return new AnonymousImportResponse(decided.get().getExperienceCount());
		}
		UUID anonymousUserId = candidateId(memberId, cookie).orElseThrow(AnonymousImportService::notAvailable);
		int moved = move(anonymousUserId, memberId);
		importRepository.save(MemberAnonymousImport.imported(memberId, anonymousUserId, moved));
		return new AnonymousImportResponse(moved);
	}

	@Transactional
	public void discard(long memberId, Optional<UUID> cookie) {
		lockMember(memberId);
		Optional<MemberAnonymousImport> decided = importRepository.findById(memberId);
		if (decided.isPresent()) {
			requireDecision(decided.get(), Decision.DISCARDED);
			return;
		}
		UUID anonymousUserId = candidateId(memberId, cookie).orElseThrow(AnonymousImportService::notAvailable);
		importRepository.save(MemberAnonymousImport.discarded(memberId, anonymousUserId));
	}

	private Optional<UUID> candidateId(long memberId, Optional<UUID> cookie) {
		if (importRepository.existsById(memberId) || experienceRepository.existsByMemberId(memberId)) {
			return Optional.empty();
		}
		return cookie.flatMap(anonymousUserRepository::findById)
			.filter(user -> Objects.equals(user.getMemberId(), memberId))
			.map(AnonymousUser::getId)
			.filter(id -> experienceRepository.countByAnonymousUser_Id(id) > 0);
	}

	/** 후보 확인과 이동 사이에 회원이 체험을 시작해 같은 사건 · 회차가 겹치면 유니크 위반이다. 한 번에 되돌리고 409로 답한다 */
	private int move(UUID anonymousUserId, long memberId) {
		try {
			return experienceRepository.moveToMember(anonymousUserId, memberId);
		} catch (DataIntegrityViolationException e) {
			throw notAvailable();
		}
	}

	private void lockMember(long memberId) {
		memberRepository.findByIdForUpdate(memberId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}

	private static void requireDecision(MemberAnonymousImport decided, Decision expected) {
		if (decided.getDecision() != expected) {
			throw notAvailable();
		}
	}

	private static BusinessException notAvailable() {
		return new BusinessException(ErrorCode.ANONYMOUS_IMPORT_NOT_AVAILABLE);
	}
}
