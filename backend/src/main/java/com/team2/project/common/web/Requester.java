package com.team2.project.common.web;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;

/**
 * 요청을 보낸 사람 (BE-140). 컨트롤러는 쿠키 · 토큰을 직접 읽지 않고 이 값을 인자로 받는다(RequesterArgumentResolver).
 *
 * @param memberId    로그인한 회원. 토큰이 없거나 회원 토큰이 아니면 empty
 * @param anonymousId 이 브라우저의 익명 ID(쿠키 NLNB_AID). 쿠키가 없거나 UUID가 아니면 empty
 */
public record Requester(Optional<Long> memberId, Optional<UUID> anonymousId) {

	/** 회원 전용 API의 회원 ID. 로그인 전이면 401 UNAUTHORIZED */
	public long requireMember() {
		return memberId.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}
}
