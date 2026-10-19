package com.team2.project.common.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 논리 DB 분리 통합 테스트 (BE-61 · WBS 7.1.4).
 * 실제 Redis(로컬 docker-compose / CI 서비스 컨테이너)에 붙는다. 다른 데이터가 있을 수 있으므로
 * 테스트마다 고유한 키를 쓰고 끝나면 지운다.
 */
@SpringBootTest
class RedisConfigTest {

	@Autowired
	private StringRedisTemplate defaultTemplate;	// @Qualifier 없이 주입 → 캐시여야 한다

	@Autowired
	@Qualifier(RedisConfig.CACHE)
	private StringRedisTemplate cacheTemplate;

	@Autowired
	@Qualifier(RedisConfig.TOKEN)
	private StringRedisTemplate tokenTemplate;

	@Autowired
	@Qualifier("cacheRedisConnectionFactory")
	private LettuceConnectionFactory cacheConnectionFactory;

	@Autowired
	@Qualifier("tokenRedisConnectionFactory")
	private LettuceConnectionFactory tokenConnectionFactory;

	@Autowired
	private ApplicationContext applicationContext;

	// 테스트마다 다른 키 (다른 테스트 · 로컬 데이터와 겹치지 않게)
	private final String key = "test:be61:" + UUID.randomUUID();

	@AfterEach
	void cleanUp() {
		cacheTemplate.delete(key);
		tokenTemplate.delete(key);
	}

	@Test
	void connectionFactories_useConfiguredDatabases() {
		// application.yml 기본값: 캐시 0 · 토큰 1
		assertThat(cacheConnectionFactory.getDatabase()).isZero();
		assertThat(tokenConnectionFactory.getDatabase()).isEqualTo(1);
	}

	@Test
	void onlyOurTwoConnectionFactoriesExist() {
		// 우리가 만든 두 팩토리 외에 Spring Boot 기본 팩토리(DB 하나만 받는)가 따로 생기면 안 된다 (RedisConfig 주석)
		assertThat(applicationContext.getBeansOfType(RedisConnectionFactory.class))
			.containsOnlyKeys("cacheRedisConnectionFactory", "tokenRedisConnectionFactory");
	}

	@Test
	void defaultTemplate_isCacheTemplate() {
		// 캐시 쪽 코드는 @Qualifier 없이 주입받는다. 실수로 토큰 DB에 쓰지 않도록 기본값은 캐시여야 한다
		assertThat(defaultTemplate).isSameAs(cacheTemplate);
	}

	@Test
	void autoConfiguredRedisTemplate_usesCacheDatabase() {
		// Spring Boot가 만드는 RedisTemplate<Object, Object>(JDK 직렬화)도 남는다. 쓰지 않는 게 규칙이지만(RedisConfig),
		// 실수로 주입받더라도 토큰 DB가 아니라 캐시 DB에 쓰여야 한다
		RedisTemplate<?, ?> autoConfigured = applicationContext.getBean("redisTemplate", RedisTemplate.class);
		assertThat(autoConfigured.getConnectionFactory()).isSameAs(cacheConnectionFactory);
	}

	@Test
	void cacheWrite_isNotVisibleFromTokenDatabase() {
		cacheTemplate.opsForValue().set(key, "cache", Duration.ofMinutes(1));

		assertThat(cacheTemplate.opsForValue().get(key)).isEqualTo("cache");
		assertThat(tokenTemplate.hasKey(key)).isFalse();
	}

	@Test
	void tokenWrite_isNotVisibleFromCacheDatabase() {
		tokenTemplate.opsForValue().set(key, "token", Duration.ofMinutes(1));

		assertThat(tokenTemplate.opsForValue().get(key)).isEqualTo("token");
		assertThat(cacheTemplate.hasKey(key)).isFalse();
	}

	@Test
	void clearingCacheDatabase_keepsTokens() {
		// 같은 이름의 키가 양쪽에 있어도 캐시를 지울 때 토큰은 남아야 한다 (DB를 나눈 이유)
		cacheTemplate.opsForValue().set(key, "cache", Duration.ofMinutes(1));
		tokenTemplate.opsForValue().set(key, "token", Duration.ofMinutes(1));

		cacheTemplate.delete(key);

		assertThat(cacheTemplate.hasKey(key)).isFalse();
		assertThat(tokenTemplate.opsForValue().get(key)).isEqualTo("token");
	}

	@Test
	void writeWithTtl_keepsExpiry() {
		// 서버가 noeviction이라 모든 키에 TTL을 단다. TTL이 실제로 붙는지 확인
		tokenTemplate.opsForValue().set(key, "token", Duration.ofMinutes(10));

		Long ttlSeconds = tokenTemplate.getExpire(key);
		assertThat(ttlSeconds).isPositive().isLessThanOrEqualTo(600L);
	}
}
