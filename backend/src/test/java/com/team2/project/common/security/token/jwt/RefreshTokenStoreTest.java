package com.team2.project.common.security.token.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 리프레시 토큰 저장 · 원자적 소비(GETDEL) (확장 단계, BE-94 · WBS 7.4.3, PR #218 리뷰) */
@SpringBootTest
class RefreshTokenStoreTest {

	@Autowired
	private RefreshTokenStore store;

	@Test
	void consume_returnsStoredValue_onlyOnce() {
		String token = "test:" + UUID.randomUUID();
		store.save(token, 99, Set.of("USER"), Duration.ofMinutes(5));

		var first = store.consume(token);
		var second = store.consume(token);

		assertThat(first).isPresent();
		assertThat(first.get().memberId()).isEqualTo(99L);
		assertThat(second).isEmpty();	// 같은 토큰을 두 번 꺼낼 수 없다(재발급 동시 요청 중복 방지)
		assertThat(store.find(token)).isEmpty();
	}

	@Test
	void consume_unknownToken_returnsEmpty() {
		assertThat(store.consume("never-issued:" + UUID.randomUUID())).isEmpty();
	}
}
