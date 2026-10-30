package com.team2.project.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 아이디 회원 만들기 — 약관 동의 기록 (BE-133). DB 없이 확인한다 */
class MemberTest {

	@Test
	void 아이디_회원은_동의_시각과_지금_약관_버전을_함께_남긴다() {
		Instant agreedAt = Instant.parse("2026-10-30T01:00:00Z");
		Member member = Member.local("내로판사", agreedAt);
		assertThat(member.getTermsAgreedAt()).isEqualTo(agreedAt);
		assertThat(member.getTermsVersion()).isEqualTo(TermsVersion.CURRENT);
	}

	@Test
	void 동의_시각_없이는_만들_수_없다() {
		// 버전만 채워진 행이 DB CHECK에서 500이 되기 전에 호출부 실수를 바로 알린다 (PR #349 리뷰)
		assertThatThrownBy(() -> Member.local("내로판사", null))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("termsAgreedAt");
	}
}
