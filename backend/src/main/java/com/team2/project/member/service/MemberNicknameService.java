package com.team2.project.member.service;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.exception.UniqueViolations;
import com.team2.project.member.domain.Member;
import com.team2.project.member.domain.Nickname;
import com.team2.project.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 닉네임 설정 · 수정(M-7)과 내 회원 정보 조회(M-8) (확장 단계, BE-139). 카카오 회원 · 아이디 회원 공통.
 * 형식은 가입과 같고(Nickname), 중복은 대소문자 구분 없이 막는다(DB 유니크 uq_member_nickname + 먼저 검사).
 * 변경 횟수 제한 · 금칙어 검사는 아직 없다(금칙어 기능 A-39 ~ A-41이 생기면 붙인다)
 */
@Service
@RequiredArgsConstructor
public class MemberNicknameService {

	private final MemberRepository memberRepository;

	/**
	 * @throws BusinessException VALIDATION_ERROR(형식) · DUPLICATE_NICKNAME(다른 회원이 쓰는 닉네임, 동시 요청 포함) ·
	 *                           UNAUTHORIZED(없는 회원 — 토큰은 유효한데 회원 행이 없는 경우)
	 */
	@Transactional
	public String change(long memberId, String nickname) {
		Nickname.validate(nickname);
		Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		if (memberRepository.existsByNicknameIgnoreCaseAndIdNot(nickname, memberId)) {
			throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
		}
		member.changeNickname(nickname);
		try {
			memberRepository.saveAndFlush(member);	// 동시에 같은 닉네임을 정하면 여기서 유니크 위반
		} catch (DataIntegrityViolationException e) {
			if (UniqueViolations.isUniqueViolation(e)) {
				throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
			}
			throw e;
		}
		return member.getNickname();
	}
}
