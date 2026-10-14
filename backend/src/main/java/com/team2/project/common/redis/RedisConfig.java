package com.team2.project.common.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 논리 DB별 접속 (확장 단계, BE-61 · WBS 7.1.4). 정책: docs/tech-stack.md 2장 "Redis 운영 구성"
 *
 * 캐시(DB 0)와 토큰(DB 1)을 커넥션 팩토리부터 따로 둔다. 한 팩토리에서 DB를 바꿔 가며 쓰면
 * 실수로 다른 DB에 쓰는 일을 코드로 막을 수 없다.
 *
 * 사용법:
 * - 캐시: StringRedisTemplate을 그냥 주입받는다 (@Primary). Spring Cache 등 자동 설정도 캐시 팩토리를 쓴다
 * - 토큰(7.4.3, C): {@code @Qualifier(RedisConfig.TOKEN) StringRedisTemplate}
 * - 모든 키에 TTL을 단다. 서버가 noeviction이라 TTL 없는 키는 지워지지 않고 쌓인다
 * - Spring Boot가 자동으로 만드는 {@code RedisTemplate<Object, Object>}(빈 이름 redisTemplate)는 쓰지 않는다.
 *   JDK 직렬화라 다른 코드 · redis-cli에서 읽을 수 없다. 객체는 JSON 문자열로 바꿔 StringRedisTemplate에 넣는다
 *   (이 템플릿도 기본 팩토리인 캐시 DB를 쓴다 — RedisConfigTest)
 *
 * 나중에 캐시 · 토큰 Redis를 다른 서버로 나누면(R11) 팩토리의 호스트만 바꾸면 된다.
 */
@Configuration
@EnableConfigurationProperties(AppRedisProperties.class)
public class RedisConfig {

	/** 캐시용 템플릿 빈 이름 */
	public static final String CACHE = "cacheRedisTemplate";
	/** 토큰 저장소용 템플릿 빈 이름 (7.4.3에서 @Qualifier로 주입) */
	public static final String TOKEN = "tokenRedisTemplate";

	/** 캐시용 (기본 팩토리). 이 빈이 있으면 Spring Boot의 Redis 기본 팩토리는 만들어지지 않는다 */
	@Bean
	@Primary
	public LettuceConnectionFactory cacheRedisConnectionFactory(AppRedisProperties properties) {
		return connectionFactory(properties, properties.cacheDatabase());
	}

	/** 토큰 저장소용 */
	@Bean
	public LettuceConnectionFactory tokenRedisConnectionFactory(AppRedisProperties properties) {
		return connectionFactory(properties, properties.tokenDatabase());
	}

	@Bean(CACHE)
	@Primary
	public StringRedisTemplate cacheRedisTemplate(
		@Qualifier("cacheRedisConnectionFactory") LettuceConnectionFactory connectionFactory) {
		return new StringRedisTemplate(connectionFactory);
	}

	@Bean(TOKEN)
	public StringRedisTemplate tokenRedisTemplate(
		@Qualifier("tokenRedisConnectionFactory") LettuceConnectionFactory connectionFactory) {
		return new StringRedisTemplate(connectionFactory);
	}

	/** 호스트 · 포트 · 비밀번호 · 타임아웃은 같고 DB 번호만 다르다 */
	private LettuceConnectionFactory connectionFactory(AppRedisProperties properties, int database) {
		RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(properties.host(), properties.port());
		server.setDatabase(database);
		if (properties.hasPassword()) {
			server.setPassword(RedisPassword.of(properties.password()));
		}
		// 명령 응답 대기 상한. 넘으면 예외를 던지고, 토큰 쪽은 500(토큰 오류로 바꾸지 않음, 기술 스택 3장)이 된다
		LettuceClientConfiguration client = LettuceClientConfiguration.builder()
			.commandTimeout(properties.timeout())
			.build();
		return new LettuceConnectionFactory(server, client);
	}
}
