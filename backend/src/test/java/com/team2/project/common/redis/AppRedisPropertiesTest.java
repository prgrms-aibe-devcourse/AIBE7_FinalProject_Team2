package com.team2.project.common.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** Redis 설정 값 검사 단위 테스트 (BE-61). Redis 없이 돈다 */
class AppRedisPropertiesTest {

	@Test
	void sameDatabaseForCacheAndToken_isRejected() {
		// 같은 DB면 캐시를 비울 때 토큰까지 지워진다 → 기동 시점에 막는다
		assertThatThrownBy(() -> properties("pw", 0, 0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("cache-database")
			.hasMessageContaining("token-database");
	}

	@Test
	void toString_masksPassword() {
		String text = properties("super-secret", 0, 1).toString();

		assertThat(text).doesNotContain("super-secret").contains("password=****");
	}

	@Test
	void blankPassword_meansNoAuth() {
		// 로컬 docker-compose는 비밀번호 없이 띄운다 (REDIS_PASSWORD 기본값이 빈 문자열)
		assertThat(properties("", 0, 1).hasPassword()).isFalse();
		assertThat(properties(null, 0, 1).hasPassword()).isFalse();
		assertThat(properties("pw", 0, 1).hasPassword()).isTrue();
		assertThat(properties("", 0, 1).toString()).contains("password=(없음)");
	}

	private AppRedisProperties properties(String password, int cacheDatabase, int tokenDatabase) {
		return new AppRedisProperties("localhost", 6379, password, Duration.ofSeconds(2), cacheDatabase, tokenDatabase);
	}
}
