package com.team2.project.common.owner;

import java.util.UUID;
import java.util.function.Function;

/**
 * 체험 · 성향 테스트 결과의 주인 (BE-140, ERD v1.50). 손님도 쓰는 두 데이터는 회원 또는 익명 ID 중 정확히 하나가 주인이다
 * (DB CHECK chk_experience_owner · chk_personality_result_owner).
 * 저장소는 {@link #fold}로 어느 쪽 쿼리를 부를지 고른다
 */
public sealed interface Owner {

	/** 회원이면 member, 손님이면 anonymous를 적용한다 */
	<T> T fold(Function<Long, T> member, Function<UUID, T> anonymous);

	/** 회원 주인 */
	record MemberOwner(long id) implements Owner {
		@Override
		public <T> T fold(Function<Long, T> member, Function<UUID, T> anonymous) {
			return member.apply(id);
		}
	}

	/** 손님 주인(브라우저 쿠키의 익명 ID) */
	record AnonymousOwner(UUID id) implements Owner {
		@Override
		public <T> T fold(Function<Long, T> member, Function<UUID, T> anonymous) {
			return anonymous.apply(id);
		}
	}
}
