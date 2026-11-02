package com.team2.project.member.service;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.member.domain.Member;
import com.team2.project.member.repository.MemberRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기존 회원의 약관 동의 기록 (확장 단계, BE-145, API 명세 M-14). 동의 기록이 없는 회원(카카오 · BE-133 이전 아이디 회원)이 대상이다 */
@Service
@RequiredArgsConstructor
public class MemberTermsService {

	private final MemberRepository memberRepository;
	private final Clock clock;

	/**
	 * 동의를 기록한다. 이미 동의한 회원이면 아무것도 바꾸지 않는다(그때 동의한 시각 · 버전을 남긴다).
	 *
	 * @throws BusinessException UNAUTHORIZED — 토큰은 유효한데 회원 행이 없는 경우
	 */
	@Transactional
	public void agree(long memberId) {
		Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		member.agreeTerms(clock.instant());
	}
}
