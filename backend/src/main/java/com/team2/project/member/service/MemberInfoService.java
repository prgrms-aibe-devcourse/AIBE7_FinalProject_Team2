package com.team2.project.member.service;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.member.domain.Member;
import com.team2.project.member.domain.MemberPassword;
import com.team2.project.member.domain.Nickname;
import com.team2.project.member.dto.MemberMeResponse;
import com.team2.project.member.repository.MemberPasswordRepository;
import com.team2.project.member.repository.MemberRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 회원 정보 조회 (확장 단계, BE-139, API 명세 M-8). 개인정보는 저장하지 않으므로(BE-62) 닉네임 · 로그인 방식 · 아이디 · 시각만 내려 준다 */
@Service
@RequiredArgsConstructor
public class MemberInfoService {

	/** API 명세 1-1: 시각은 +09:00 오프셋으로 내려준다 */
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final MemberRepository memberRepository;
	private final MemberPasswordRepository passwordRepository;

	/** @throws BusinessException UNAUTHORIZED — 토큰은 유효한데 회원 행이 없는 경우 */
	@Transactional(readOnly = true)
	public MemberMeResponse me(long memberId) {
		Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		String loginId = passwordRepository.findById(memberId).map(MemberPassword::getLoginId).orElse(null);
		return new MemberMeResponse(member.getNickname(), Nickname.displayName(memberId, member.getNickname()),
			member.getProvider().name(), loginId, kst(member.getCreatedAt()), kst(member.getLastLoginAt()));
	}

	private static OffsetDateTime kst(Instant instant) {
		return instant == null ? null : instant.atZone(KST).toOffsetDateTime();
	}
}
